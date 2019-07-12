#!/bin/sh

FILE=images/linux/BOOT.BIN

if [ -e $FILE ]; then
  rm $FILE
fi

petalinux-package --boot --fsbl images/linux/zynq_fsbl.elf --fpga images/linux/system.bit --u-boot
