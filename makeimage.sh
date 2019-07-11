#!/bin/sh

rm images/linux/BOOT.BIN
petalinux-package --boot --fsbl images/linux/zynq_fsbl.elf --fpga images/linux/system.bit --u-boot
