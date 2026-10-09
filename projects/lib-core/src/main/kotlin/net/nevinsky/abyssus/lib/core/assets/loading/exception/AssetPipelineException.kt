package net.nevinsky.abyssus.lib.core.assets.loading.exception

import net.nevinsky.abyssus.lib.core.assets.loading.AssetState

open class AssetPipelineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class AssetAbsentException(val asset: String) :
    AssetPipelineException("Asset '$asset' has no usable files")

class MissingDependencyException(val asset: String, val dependency: String) :
    AssetPipelineException("Asset '$asset' depends on '$dependency', which is not loaded")

class DependencyFailedException(val asset: String, val dependency: String, cause: Throwable) :
    AssetPipelineException("Asset '$asset' depends on '$dependency', which failed to load", cause)

class CyclicDependencyException(val cycle: List<String>) :
    AssetPipelineException("Cyclic asset dependencies: ${cycle.joinToString(" -> ")}")

class AmbiguousLoaderException(val asset: String) :
    AssetPipelineException("More than one loader handles asset '$asset'")

class AssetNotReadyException(val asset: String, val state: AssetState?) :
    AssetPipelineException("Asset '$asset' is not built (state=$state)")

class MissingBuiltAssetException(val asset: String) :
    AssetPipelineException("Built asset '$asset' is not available")
