# Canonical environment for the Abyssus UI tests and their screenshot baselines (see docs/ai/testing.md):
# Ubuntu 24.04, Xvfb 1920x1080x24, UTC, English locale, JDK 21 for Gradle. The IDE brings its own JBR and fonts.
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=en_US.UTF-8 LANGUAGE=en_US:en LC_ALL=en_US.UTF-8 \
    TZ=UTC \
    DISPLAY=:99 \
    ABYSSUS_UI_SCREEN=1920x1080x24 \
    LIBGL_ALWAYS_SOFTWARE=1

RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl git rsync procps unzip locales tzdata \
        openjdk-21-jdk-headless \
        xvfb x11-utils xauth openbox \
        fontconfig fonts-dejavu-core \
        libxtst6 libxrender1 libxi6 libxrandr2 libxext6 libx11-6 libxcursor1 libxinerama1 libxxf86vm1 \
        libfreetype6 libgl1 libglx-mesa0 libgl1-mesa-dri libegl1 mesa-utils libgbm1 libasound2t64 libnss3 libgtk-3-0t64 \
    && locale-gen en_US.UTF-8 \
    && ln -sf /usr/share/zoneinfo/UTC /etc/localtime \
    && rm -rf /var/lib/apt/lists/*

# Package versions the screenshot environment manifest is checked against (read by the tests at run time).
RUN { for p in fonts-dejavu-core fontconfig xvfb openbox libfreetype6; do echo "$p=$(dpkg-query -W -f='${Version}' $p)"; done; } > /etc/abyssus-ui-environment

# The repo is copied into the container (see scripts/run-ui-tests-docker.sh), never written in place.
RUN useradd --create-home --shell /bin/bash ui && mkdir -p /workspace /out /home/ui/.gradle && chown -R ui:ui /workspace /out /home/ui
USER ui
WORKDIR /workspace
ENV GRADLE_USER_HOME=/home/ui/.gradle

COPY --chown=ui:ui entrypoint.sh /usr/local/bin/ui-entrypoint
ENTRYPOINT ["/usr/local/bin/ui-entrypoint"]
CMD ["bash"]
