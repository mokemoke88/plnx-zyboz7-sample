SUMMARY = "Recipe for build an external udmabuf Linux kernel module"
SECTION = "PETALINUX/modules"
LICENSE = "BSD-2-Clause"

inherit module

SRC_URI = "git://github.com/ikwzm/udmabuf.git;protocol=git;tag=v1.4.2 \
  file://Makefile.patch \
"
LIC_FILES_CHKSUM = "file://${WORKDIR}/git/LICENSE;md5=bebf0492502927bef0741aa04d1f35f5"
S = "${WORKDIR}/git/"

# The inherit of module.bbclass will automatically name module packages with
# "kernel-module-" prefix as required by the oe-core build environment.
