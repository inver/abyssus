/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.jolt


import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.github.stephengold.joltjni.*
import com.github.stephengold.joltjni.enumerate.EActivation
import com.github.stephengold.joltjni.enumerate.EMotionType
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties
import net.nevinsky.abyssus.lib.gdx.util.EcsUtils.Companion.NO_ENTITY
import net.nevinsky.abyssus.lib.gdx.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.gdx.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.gdx.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.gdx.scene.SceneEngine
import net.nevinsky.abyssus.lib.physics.*
import org.slf4j.Logger
import kotlin.math.abs
import kotlin.math.max

private const val LAYER_STATIC = 0
private const val LAYER_MOVING = 1

/**
 * A Jolt world built from the physics components of [engine]'s entities: one body per entity with a
 * [ColliderComponent] (static without a [RigidBodyComponent]) and one constraint per [ConstraintComponent]. Bad input
 * is refused with one warning to [log] per entity, and the rest of the scene is simulated.
 *
 * [advance] runs fixed steps of [PHYSICS_STEP] and writes dynamic and kinematic poses to each entity's
 * [PositionComponent] (`localPosition` / `localRotation`; parents are ignored). One world is used by one thread at a
 * time, and runs single-threaded, so a run is deterministic on one machine. [close] releases every native object;
 * afterwards every method throws [IllegalStateException].
 *
 * Needs Jolt's natives ([net.nevinsky.abyssus.lib.physics.jolt.JoltNatives]); never use it in the IDE process.
 */
class PhysicsWorld(
    private val engine: SceneEngine,
    private val assets: PhysicsAssets,
    private val log: Logger,
    natives: JoltNatives,
) : AutoCloseable {
    private val owned = ArrayList<JoltPhysicsObject>()
    private val system: PhysicsSystem
    private val bodies: BodyInterface
    private val allocator: TempAllocatorImpl
    private val jobs: JobSystemSingleThreaded
    private val shapes: ShapeFactory

    private val records = LinkedHashMap<Entity, BodyRecord>()
    private val byBodyId = HashMap<Int, BodyRecord>()
    private val constraintList = ArrayList<ConstraintRecord>()
    private val stepContacts = LinkedHashMap<Pair<Int, Int>, Contact>()
    private val ropeTension = RopeTension(this)
    private val clock = FixedStepClock()
    private var closed = false

    private val removal = object : EntityListener {
        override fun entityAdded(entity: Entity) = Unit
        override fun entityRemoved(entity: Entity) {
            if (!closed) removeEntity(entity)
        }
    }

    init {
        natives.load()
        val pairs = own(ObjectLayerPairFilterTable(2)).apply {
            enableCollision(LAYER_MOVING, LAYER_STATIC)
            enableCollision(LAYER_MOVING, LAYER_MOVING)
        }
        val broadPhase = own(BroadPhaseLayerInterfaceTable(2, 2)).apply {
            mapObjectToBroadPhaseLayer(LAYER_STATIC, 0)
            mapObjectToBroadPhaseLayer(LAYER_MOVING, 1)
        }
        val filter = own(ObjectVsBroadPhaseLayerFilterTable(broadPhase, 2, pairs, 2))
        system = own(PhysicsSystem())
        system.init(10_240, 0, 65_536, 10_240, broadPhase, filter, pairs)
        system.setGravity(0f, -GRAVITY, 0f)
        allocator = own(TempAllocatorImpl(16 * 1024 * 1024))
        jobs = own(JobSystemSingleThreaded(Jolt.cMaxPhysicsJobs))
        bodies = system.getBodyInterface()
        shapes = ShapeFactory(assets, ::own)
        system.setContactListener(own(ContactCollector()))
        build()
        engine.addEntityListener(removal)
    }

    /** Entities that have a body. */
    val entities: Set<Entity> get() = open { records.keys.toSet() }

    /** The constraints, from the scene's components and added by the game, in creation order. */
    val constraints: List<PhysicsConstraint> get() = open { constraintList.toList() }

    /** [entity]'s body, null when it has none. */
    fun bodyOf(entity: Entity): PhysicsBody? = open { records[entity] }

    /** The bodies that touched during the last [advance] or [step], each pair once. */
    fun contacts(): List<Contact> = open { stepContacts.values.toList() }

    /**
     * Runs as many fixed steps as [seconds] and the carried remainder need, at most [MAX_STEPS_PER_ADVANCE], then writes
     * poses back. The fraction of a step left over is carried to the next call; whole steps beyond the limit are
     * dropped. Returns the number of steps run.
     */
    fun advance(seconds: Float): Int = open {
        val steps = clock.advance(seconds)
        runSteps(steps)
        steps
    }

    /** Runs exactly one fixed step and writes poses back (Step while paused). */
    fun step() = open { runSteps(1) }

    /** A rope from [anchorA] on [a] to [anchorB] on [b] (local points), or to the world point [anchorB] when [b] is null. */
    fun addRope(a: Entity, anchorA: Vector3, b: Entity?, anchorB: Vector3, length: Float): PhysicsConstraint =
        addConstraint(ConstraintKind.DISTANCE, a, anchorA, b, anchorB, 0f, length)

    /**
     * A constraint of [kind] between [a] and [b] (or the world when [b] is null): anchors as in [ConstraintComponent],
     * [minDistance] / [maxDistance] for [ConstraintKind.DISTANCE] and [hingeAxis] (local to [a]) for
     * [ConstraintKind.HINGE]. Throws [IllegalArgumentException] for a body-less entity or bad values.
     */
    fun addConstraint(
        kind: ConstraintKind, a: Entity, anchorA: Vector3, b: Entity?, anchorB: Vector3,
        minDistance: Float = 0f, maxDistance: Float = 1f, hingeAxis: Vector3 = Vector3.Y,
    ): PhysicsConstraint = open {
        val spec = ConstraintSpec(kind, a, b, Vector3(anchorA), Vector3(anchorB), minDistance, maxDistance, Vector3(hingeAxis))
        problem(spec)?.let { throw IllegalArgumentException("Cannot add the constraint of ${describe(a)}: $it") }
        create(spec)
    }

    /** Removes [constraint]; nothing happens when it is already gone. */
    fun remove(constraint: PhysicsConstraint) = open {
        val record = constraint as? ConstraintRecord ?: return@open
        if (constraintList.remove(record)) {
            system.removeConstraint(record.ref.getPtr())
            record.ref.close()
        }
    }

    /** Takes [entity]'s body and every constraint that uses it out of the simulation. Removing it from the engine does too. */
    fun removeEntity(entity: Entity) = open {
        for (c in constraintList.filter { it.entity == entity || it.other == entity }) remove(c)
        val record = records.remove(entity) ?: return@open
        byBodyId.remove(record.id)
        bodies.removeBody(record.id)
        bodies.destroyBody(record.id)
    }

    override fun close() {
        if (closed) return
        engine.removeEntityListener(removal)
        for (c in constraintList.asReversed()) {
            system.removeConstraint(c.ref.getPtr())
            c.ref.close()
        }
        constraintList.clear()
        for (r in records.values.reversed()) {
            bodies.removeBody(r.id)
            bodies.destroyBody(r.id)
        }
        records.clear()
        byBodyId.clear()
        system.setContactListener(null)
        for (o in owned.asReversed()) o.close()
        owned.clear()
        closed = true
    }

    // ---- building ----

    private fun build() {
        val ids = engine.ids.ids.sorted()
        for (id in ids) {
            val entity = engine.ids[id] ?: continue
            val body = entity.getComponent(RigidBodyComponent::class.java)
            val collider = entity.getComponent(ColliderComponent::class.java)
            if (collider == null) {
                if (body != null) warn(entity, "has a rigid body but no collider, so it is left out of the simulation")
                continue
            }
            createBody(entity, body, collider)?.let { problem -> warn(entity, "$problem; it is left out of the simulation") }
        }
        for (id in ids) {
            val entity = engine.ids[id] ?: continue
            val c = entity.getComponent(ConstraintComponent::class.java) ?: continue
            val other = if (c.other == NO_ENTITY) null else engine.ids[c.other]
            if (c.other != NO_ENTITY && other == null) {
                warn(entity, "its constraint names entity ${c.other}, which is not in the scene; the constraint is left out")
                continue
            }
            val spec = ConstraintSpec(c.kind, entity, other, Vector3(c.anchor), Vector3(c.otherAnchor), c.minDistance, c.maxDistance, Vector3(c.hingeAxis))
            val problem = problem(spec)
            if (problem != null) warn(entity, "$problem; the constraint is left out") else create(spec)
        }
    }

    /** Creates [entity]'s body; returns why it cannot instead. */
    private fun createBody(entity: Entity, body: RigidBodyComponent?, collider: ColliderComponent): String? {
        val motion = body?.motionType ?: MotionType.STATIC
        // a moving body's pose is written back, so it needs a position component
        val position = entity.getComponent(PositionComponent::class.java)
            ?: PositionComponent().also { if (motion != MotionType.STATIC) entity.add(it) }
        listOfNotNull(
            nonFinite("PositionComponent.localPosition", position.localPosition),
            nonFinite("PositionComponent.localScale", position.localScale),
            nonFinite("PositionComponent.localRotation", position.localRotation.x, position.localRotation.y, position.localRotation.z, position.localRotation.w),
        ).firstOrNull()?.let { return it }
        if (body != null) {
            listOf("mass" to body.mass, "friction" to body.friction, "restitution" to body.restitution,
                "linearDamping" to body.linearDamping, "angularDamping" to body.angularDamping, "gravityFactor" to body.gravityFactor,
            ).firstOrNull { !it.second.isFinite() }?.let { return "RigidBodyComponent.${it.first} is not finite (${it.second})" }
            if (motion == MotionType.DYNAMIC && body.mass <= 0f) return "RigidBodyComponent.mass ${body.mass} is not greater than 0"
            if (body.friction < 0f || body.linearDamping < 0f || body.angularDamping < 0f || body.restitution < 0f) {
                return "RigidBodyComponent has a negative friction, restitution or damping"
            }
        }
        if (collider.shape == ColliderShape.HEIGHT_FIELD && motion != MotionType.STATIC) return "a height field can only be static, not $motion"
        val rotation = Quaternion(position.localRotation).nor()
        val shape = when (val built = shapes.build(entity, collider, position.localScale)) {
            is ShapeFactory.Built.Refused -> return built.reason
            is ShapeFactory.Built.Shape -> {
                built.warning?.let { warn(entity, it) }
                built.ref
            }
        }
        if (entity.getComponent(ParentComponent::class.java)?.parentEntityId?.let { it != NO_ENTITY } == true) {
            warn(entity, "has a parent, which physics ignores: its body moves in world space")
        }
        val p = position.localPosition
        val settings = own(BodyCreationSettings(
            shape, RVec3(p.x.toDouble(), p.y.toDouble(), p.z.toDouble()), Quat(rotation.x, rotation.y, rotation.z, rotation.w),
            jolt(motion), if (motion == MotionType.STATIC) LAYER_STATIC else LAYER_MOVING,
        ))
        settings.setFriction(body?.friction ?: 0.2f)
        settings.setRestitution(body?.restitution ?: 0f)
        if (body != null) {
            settings.setLinearDamping(body.linearDamping)
            settings.setAngularDamping(body.angularDamping)
            settings.setGravityFactor(body.gravityFactor)
            if (motion == MotionType.DYNAMIC) {
                settings.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia)
                settings.setMassPropertiesOverride(own(MassProperties()).setMass(body.mass))
            }
        }
        settings.setUserData(entityId(entity).toLong())
        val created = bodies.createBody(settings)
        val id = created.getId()
        bodies.addBody(id, if (motion == MotionType.STATIC) EActivation.DontActivate else EActivation.Activate)
        val record = BodyRecord(entity, id, created, motion, if (motion == MotionType.DYNAMIC) body!!.mass else 0f, body?.gravityFactor ?: 1f)
        records[entity] = record
        byBodyId[id] = record
        return null
    }

    private fun jolt(motion: MotionType) = when (motion) {
        MotionType.STATIC -> EMotionType.Static
        MotionType.KINEMATIC -> EMotionType.Kinematic
        MotionType.DYNAMIC -> EMotionType.Dynamic
    }

    private class ConstraintSpec(
        val kind: ConstraintKind, val a: Entity, val b: Entity?, val anchorA: Vector3, val anchorB: Vector3,
        val minDistance: Float, val maxDistance: Float, val hingeAxis: Vector3,
    )

    /** Why [spec] cannot be created; null when it can. */
    private fun problem(spec: ConstraintSpec): String? {
        nonFinite("ConstraintComponent.anchor", spec.anchorA)?.let { return it }
        nonFinite("ConstraintComponent.otherAnchor", spec.anchorB)?.let { return it }
        nonFinite("ConstraintComponent.hingeAxis", spec.hingeAxis)?.let { return it }
        if (!spec.minDistance.isFinite()) return "ConstraintComponent.minDistance is not finite (${spec.minDistance})"
        if (!spec.maxDistance.isFinite()) return "ConstraintComponent.maxDistance is not finite (${spec.maxDistance})"
        if (spec.kind == ConstraintKind.DISTANCE) {
            if (spec.minDistance < 0f) return "ConstraintComponent.minDistance ${spec.minDistance} is below 0"
            if (spec.maxDistance < spec.minDistance) {
                return "ConstraintComponent.maxDistance ${spec.maxDistance} is below its minDistance ${spec.minDistance}"
            }
        }
        if (spec.kind == ConstraintKind.HINGE && spec.hingeAxis.isZero) return "ConstraintComponent.hingeAxis is zero"
        if (spec.a !in records) return "it has no body"
        if (spec.b != null && spec.b !in records) return "the other entity ${describe(spec.b)} has no body"
        if (spec.b == spec.a) return "it is joined to itself"
        return null
    }

    private fun create(spec: ConstraintSpec): ConstraintRecord {
        val a = records.getValue(spec.a)
        val b = spec.b?.let { records.getValue(it) }
        val worldA = transformOf(spec.a).let { Vector3(spec.anchorA).mul(it) }
        val worldB = spec.b?.let { Vector3(spec.anchorB).mul(transformOf(it)) } ?: Vector3(spec.anchorB)
        val settings: TwoBodyConstraintSettings = when (spec.kind) {
            ConstraintKind.DISTANCE -> own(DistanceConstraintSettings()).apply {
                setPoint1(worldA.x.toDouble(), worldA.y.toDouble(), worldA.z.toDouble())
                setPoint2(worldB.x.toDouble(), worldB.y.toDouble(), worldB.z.toDouble())
                setMinDistance(spec.minDistance)
                setMaxDistance(spec.maxDistance)
            }
            ConstraintKind.HINGE -> own(HingeConstraintSettings()).apply {
                val axis = Vector3(spec.hingeAxis).mul(rotationOf(spec.a)).nor()
                val normal = perpendicular(axis)
                setPoint1(worldA.x.toDouble(), worldA.y.toDouble(), worldA.z.toDouble())
                setPoint2(worldB.x.toDouble(), worldB.y.toDouble(), worldB.z.toDouble())
                setHingeAxis1(axis.x, axis.y, axis.z)
                setHingeAxis2(axis.x, axis.y, axis.z)
                setNormalAxis1(normal.x, normal.y, normal.z)
                setNormalAxis2(normal.x, normal.y, normal.z)
            }
            ConstraintKind.FIXED -> own(FixedConstraintSettings()).apply { setAutoDetectPoint(true) }
        }
        val constraint = settings.create(a.body, b?.body ?: Body.sFixedToWorld())
        val ref = constraint.toRef()
        system.addConstraint(ref)
        bodies.activateBody(a.id)
        b?.let { bodies.activateBody(it.id) }
        val local = Vector3(spec.anchorA)
        val otherLocal = Vector3(spec.anchorB)
        return ConstraintRecord(spec.kind, spec.a, spec.b, ref, local, otherLocal, spec.maxDistance).also { constraintList += it }
    }

    private fun perpendicular(axis: Vector3): Vector3 {
        val helper = if (abs(axis.y) < 0.9f) Vector3.Y else Vector3.X
        return Vector3(axis).crs(helper).nor()
    }

    // ---- stepping ----

    private fun runSteps(steps: Int) {
        stepContacts.clear()
        repeat(steps) {
            for (r in records.values) r.applyPending(bodies)
            val ropes = ropeTension.before(constraintList)
            val error = system.update(PHYSICS_STEP, 1, allocator, jobs)
            check(error == 0) { "Jolt failed to step (error $error)" }
            ropes.after()
        }
        for (r in records.values) r.clearPending()
        writeBack()
    }

    private fun writeBack() {
        val p = RVec3()
        val q = Quat()
        for (r in records.values) {
            if (r.motion == MotionType.STATIC) continue
            bodies.getPositionAndRotation(r.id, p, q)
            val position = r.entity.getComponent(PositionComponent::class.java) ?: continue
            position.localPosition.set(p.x(), p.y(), p.z())
            position.localRotation.set(q.getX(), q.getY(), q.getZ(), q.getW())
        }
    }

    // ---- what rope tension needs ----

    internal fun recordOf(entity: Entity?): BodyRecord? = entity?.let { records[it] }
    internal fun velocityOf(record: BodyRecord, out: Vector3): Vector3 {
        val v = bodies.getLinearVelocity(record.id)
        return out.set(v.getX(), v.getY(), v.getZ())
    }
    internal fun anchorsOf(c: ConstraintRecord, a: Vector3, b: Vector3) {
        a.set(c.anchor).mul(bodyTransform(records.getValue(c.entity)))
        val other = c.other?.let { records[it] }
        if (other != null) b.set(c.otherAnchor).mul(bodyTransform(other)) else b.set(c.otherAnchor)
    }

    private fun bodyTransform(record: BodyRecord): Matrix4 {
        val p = RVec3()
        val q = Quat()
        bodies.getPositionAndRotation(record.id, p, q)
        val scale = record.entity.getComponent(PositionComponent::class.java)?.localScale ?: Vector3(1f, 1f, 1f)
        return Matrix4().set(Vector3(p.x(), p.y(), p.z()), Quaternion(q.getX(), q.getY(), q.getZ(), q.getW()), scale)
    }

    // ---- helpers ----

    private fun transformOf(entity: Entity): Matrix4 =
        Matrix4((entity.getComponent(PositionComponent::class.java) ?: PositionComponent()).getTransform())

    private fun rotationOf(entity: Entity): Quaternion =
        Quaternion(entity.getComponent(PositionComponent::class.java)?.localRotation ?: Quaternion()).nor()

    private fun nonFinite(name: String, v: Vector3): String? = nonFinite(name, v.x, v.y, v.z)
    private fun nonFinite(name: String, vararg values: Float): String? =
        if (values.all { it.isFinite() }) null else "$name is not finite (${values.joinToString(", ")})"

    private fun entityId(entity: Entity): Int = engine.ids.ids.firstOrNull { engine.ids[it] === entity } ?: NO_ENTITY

    private fun describe(entity: Entity): String {
        val name = entity.getComponent(NameComponent::class.java)?.name
        val id = entityId(entity)
        return if (name.isNullOrEmpty()) "entity $id" else "$name (entity $id)"
    }

    private fun warn(entity: Entity, message: String) = log.warn("Physics: ${describe(entity)} $message")

    private fun <T : JoltPhysicsObject> own(o: T): T = o.also { owned += it }

    private inline fun <T> open(block: () -> T): T {
        check(!closed) { "physics world is closed" }
        return block()
    }

    /** Collects contacts during a step, on the stepping thread (the job system is single-threaded). */
    private inner class ContactCollector : CustomContactListener() {
        override fun onContactAdded(body1Va: Long, body2Va: Long, manifoldVa: Long, settingsVa: Long) = collect(body1Va, body2Va, true)
        override fun onContactPersisted(body1Va: Long, body2Va: Long, manifoldVa: Long, settingsVa: Long) = collect(body1Va, body2Va, false)

        private fun collect(body1Va: Long, body2Va: Long, first: Boolean) {
            val b1 = Body(body1Va)
            val b2 = Body(body2Va)
            val r1 = byBodyId[b1.getId()] ?: return
            val r2 = byBodyId[b2.getId()] ?: return
            val v1 = b1.getLinearVelocity()
            val v2 = b2.getLinearVelocity()
            val speed = Vector3(v1.getX() - v2.getX(), v1.getY() - v2.getY(), v1.getZ() - v2.getZ()).len()
            val (a, b) = if (entityId(r1.entity) <= entityId(r2.entity)) r1 to r2 else r2 to r1
            val key = a.id to b.id
            val previous = stepContacts[key]
            stepContacts[key] = Contact(a.entity, b.entity, max(speed, previous?.relativeSpeed ?: 0f), first || previous?.first == true)
        }
    }

    /** A body and what the game applied to it since the last [advance]. */
    internal inner class BodyRecord(
        override val entity: Entity,
        val id: Int,
        val body: Body,
        override val motionType: MotionType,
        override val mass: Float,
        val gravityFactor: Float,
    ) : PhysicsBody {
        val motion get() = motionType
        val force = Vector3()
        val torque = Vector3()

        override fun applyForce(force: Vector3) = open { this.force.add(force); Unit }
        override fun applyTorque(torque: Vector3) = open { this.torque.add(torque); Unit }

        override fun velocity(out: Vector3): Vector3 = open { velocityOf(this, out) }

        override fun angularVelocity(out: Vector3): Vector3 = open {
            val w = bodies.getAngularVelocity(id)
            out.set(w.getX(), w.getY(), w.getZ())
        }

        override fun setVelocity(velocity: Vector3) = open {
            bodies.setLinearVelocity(id, Vec3(velocity.x, velocity.y, velocity.z))
            bodies.activateBody(id)
        }

        override fun moveKinematic(position: Vector3, rotation: Quaternion, seconds: Float) = open {
            check(motionType == MotionType.KINEMATIC) { "${describe(entity)} is not kinematic" }
            val q = Quaternion(rotation).nor()
            bodies.moveKinematic(id, RVec3(position.x.toDouble(), position.y.toDouble(), position.z.toDouble()), Quat(q.x, q.y, q.z, q.w), seconds)
        }

        override fun setPose(position: Vector3, rotation: Quaternion) = open {
            check(motionType != MotionType.STATIC) { "${describe(entity)} is static" }
            val q = Quaternion(rotation).nor()
            bodies.setPositionAndRotation(id, position.x.toDouble(), position.y.toDouble(), position.z.toDouble(), q.x, q.y, q.z, q.w, EActivation.Activate)
            bodies.setLinearAndAngularVelocity(id, 0f, 0f, 0f, 0f, 0f, 0f)
            entity.getComponent(PositionComponent::class.java)?.let {
                it.localPosition.set(position)
                it.localRotation.set(q)
            }
            Unit
        }

        fun applyPending(bodies: BodyInterface) {
            if (motionType != MotionType.DYNAMIC) return
            if (!force.isZero) bodies.addForce(id, force.x, force.y, force.z)
            if (!torque.isZero) bodies.addTorque(id, torque.x, torque.y, torque.z)
            if (!force.isZero || !torque.isZero) bodies.activateBody(id)
        }

        fun clearPending() {
            force.setZero()
            torque.setZero()
        }

        /** The acceleration the game's force and gravity give this body during a step. */
        fun acceleration(out: Vector3): Vector3 =
            out.set(force).scl(if (mass > 0f) 1f / mass else 0f).add(0f, -GRAVITY * gravityFactor, 0f)
    }

    internal inner class ConstraintRecord(
        override val kind: ConstraintKind,
        override val entity: Entity,
        override val other: Entity?,
        val ref: TwoBodyConstraintRef,
        val anchor: Vector3,
        val otherAnchor: Vector3,
        val maxDistance: Float,
    ) : PhysicsConstraint {
        override var tension = 0f
            internal set

        override fun anchors(a: Vector3, b: Vector3) = open { anchorsOf(this, a, b) }

        /** Whether tension is measured: a distance constraint, which pulls when its anchors reach [maxDistance]. */
        val rope get() = kind == ConstraintKind.DISTANCE
    }
}
