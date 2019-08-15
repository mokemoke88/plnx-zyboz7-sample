# ZyboZ7-plnx

ZyboZ7-20で使用するPetaLinux環境の構築 試行記録  
作業していて、思ったことを書き連ねています.

## TODOリスト

- [X] USB(ホストモード) を利用可能にする
- [X] Ethernetを利用可能にする 
- [ ] udmabuf のカスタムモジュールレシピ 追加
- [X] uboot にZyboZ7-20用のMACアドレス読み込みパッチ 追加
- [X] SDKの作成
- [X] C++ でビルドしたバイナリが動作しない
- [X] telnetd, ftpd の無効化
- [X] dropbear(ssh server)の起動時オプション変更
- [ ] ユーザの追加

## 新規プロジェクト作成からビルドまでの流れ

- 新規プロジェクト作成 petalinux-create -t project --template=zynq -n <プロジェクト名>
- ハードウェアデザインの取り込みと初期設定
- イメージビルド
- ブート用イメージの生成
- ブート用のイメージ導入

### 新規プロジェクト作成

以下を実行. 指定したプロジェクト名ディレクトリ以下にpetalinuxプロジェクトが生成される.

```
> petalinux-create -t project --template=zynq -n <プロジェクト名>
```

以降の作業はプロジェクト名ディレクトリ以下で実施.

### ハードウェアデザインの取り込みと初期設定

あらかじめvivadoの[FILE]->[EXPORT]->[EXPORT HARDWARE]で出力した.hdfファイルをどこかのディレクトリに配置しておく.  
(vivadoプロジェクト配下のxxxxx.sdkディレクトリを持ってきても可)

以下を実行.

```
> petalinux-config --get-hwdescripton=<.hdfファイルがあるディレクトリ>
```

petalinuxのトップレベル設定画面が表示されるがそのまま終了.  
ハードウェアデザインに変更を行った際は, 同様にして取り込む.

### イメージビルド

以下を実行.

```
> petalinux-build
```

成果物は images/linux 以下に出力される.

### ブート用のイメージ生成

Zybo起動時に最初に読み込まれるBOOT.BINが生成されていないので以下を実行して生成する.

```
> petalinux-package --boot --fsbl images/linux/zynq_fsbl.elf --fpga images/linux/system.bit --u-boot
```

これでブートに必要な

- BOOT.BIN
- image.ub

が images/linux 以下に生成される.

*) 手打ちしてられないのでシェルスクリプト化 -> createBootBin.sh

### ブート用のイメージ導入

生成した BOOT.BIN image.ub を第1パーティションがFATでフォーマットされたSDカードに書き込む.

## USB(ホストモード)を利用可能にする

デバイスツリー上で USBコントローラに使用するPHYの指定とオプションが不十分なので
project-spec/meta-user/recipes-bsp/device-tree/files/system-user.dtsi に以下を追加する.
(dtsを編集する際は, 同様に上記のレシピを操作する)

```
/{
  usb_phy0: usb_phy@0 {
    compatible = "ulpi-phy";
    #phy-cells = <0>;
    reg = <0xe0002000 0x1000>;
    view-port = <0x0170>;
    drv-vbus;
  };
};

&usb0 {
    compatible = "xlnx,zynq-usb-2.20a", "chipidea,usb2";
    status = "okay";
    clocks = <0x1 0x1c>;
    dr_mode = "host";
    interrupt-parent = <0x4>;
    interrupts = <0x0 0x15 0x4>;
    reg = <0xe0002000 0x1000>;
    usb-phy = <&usb_phy0>;
};
```

(2019.07.14 追記)

usb周りの記述を zyboz7-usb.dtsi に移し, system-user.dtsi はそれを取り込むように変更した.  
USB周りもpetalinuxが提供している値と重複している箇所は削除した.

files/system-user.dtsi
```
/include/ "system-conf.dtsi"
/include/ "zyboz7-usb.dtsi"

/ {
};

```

files/zyboz7-usb.dtsi
```
/ {
  usb_phy0: usb_phy@0 {
    compatible = "usb-nop-xceiv";
    #phy-cells = <0>;
  };
};

&usb0 {
    status = "okay";
    dr_mode = "host";
    usb-phy = <&usb_phy0>;
};
```

device-tree.bbapend を,追加したファイルを取り込むように変更

device-tree.bbapend
```
FILESEXTRAPATHS_prepend := "${THISDIR}/files:"

SRC_URI += "\
  file://system-user.dtsi \
  file://zyboz7-usb.dtsi \
"
```

### 参考URI

- Ubuntu+ZynqでUSB-HOSTが機能しないときの解決方法(Qiita): https://qiita.com/lp6m/items/45d90e9d2443c2198e96
- Petalinux 2015.2.1 usb not working (Xilinx フォーラム): https://forums.xilinx.com/t5/Embedded-Linux/Petalinux-2015-2-1-usb-not-working/td-p/654349

## Ethernetを利用可能にする

特になにもしなくてよい.
vivado 側での設定になるが、2018.3辺りまでのDiglintのボード設定ファイルだとETH0のMIO接続がEMIOになっていたりするので
そちらを確認.
(ただしこのままだとMACアドレスは手動で書き換える必要あり)

## telnetd, ftpd の無効化

busyboxがrootfsにインストールする/etc/inetd.confの該当箇所をコメントアウト方向で検討.

該当ファイルをどのレシピが生成しているのかを調べるのに  
build/tmp/work/以下のログを追いかけていって  
/build/tmp/work/cortexa9t2hf-neon-xilinx-linux-gnueabi/busybox/1.29.2-r0/temp/log.do_unpack  
に行き着いた.

対象レシピは project_spec/meta-plnx-generated/recipes-core/busybox のようなので  
一旦 project_spec/meta-user/recipies-core 以下にコピーする.

files/ineted.confの下2行 telenet... をコメントアウト

*) meta-plnx-generated 以下のレシピなので petalinux-config -c busybox とかで行ける？

2019.07.14 追記

petalinux-config -c busybox でinetd自体を止めるのがシンプルそう.

```
> petalinux-config -c busybox
```

で変更, 結果は meta-user/recipes-core/busybox 以下に保存される.


## uboot にZyboZ7-20用のMACアドレス読み込みパッチ 追加

ユーザ用のu-bootレシピは project-spec/meta-user/recipes-bsp/u-boot に用意されているので
これに追加する方向で実施する.

ubootの展開されたソースファイルへアクセスするために  
project-spec/meta-user/conf/petalinuxbsp.conf に以下を追記

```
RM_WORK_EXCLUDE += "u-boot-xlnx"
```

ソースを展開するためにpetalinux-build -c u-boot を実行  
ソースの展開先は  
build/tmp/work/plnx_zynq7-xilinx-linux-gnueabi/u-boot-xlnx/v2019.01-xilinx-v2019.1+gitAUTOINC+d895ac5e94-r0/git

展開した先の board/xilinx/common/board.c に spi関係のインクルードファイルと  
SPIフラッシュからMACアドレスを読み込む処理を追加

```
// SPDX-License-Identifier: GPL-2.0+
/*
 * (C) Copyright 2014 - 2019 Xilinx, Inc.
 * Michal Simek <michal.simek@xilinx.com>
 */

#include <common.h>
#include <dm/uclass.h>
#include <i2c.h>
#include <spi.h>
#include <spi_flash.h>

int zynq_board_read_rom_ethaddr(unsigned char *ethaddr)
{
	int ret = -EINVAL;

#if defined(CONFIG_ZYNQ_GEM_I2C_MAC_OFFSET)
	struct udevice *dev;
	ofnode eeprom;

	eeprom = ofnode_get_chosen_node("xlnx,eeprom");
	if (!ofnode_valid(eeprom))
		return -ENODEV;

	debug("%s: Path to EEPROM %s\n", __func__,
	      ofnode_get_chosen_prop("xlnx,eeprom"));

	ret = uclass_get_device_by_ofnode(UCLASS_I2C_EEPROM, eeprom, &dev);
	if (ret)
		return ret;

	ret = dm_i2c_read(dev, CONFIG_ZYNQ_GEM_I2C_MAC_OFFSET, ethaddr, 6);
	if (ret)
		debug("%s: I2C EEPROM MAC address read failed\n", __func__);
	else
		debug("%s: I2C EEPROM MAC %pM\n", __func__, ethaddr);
#endif

#if defined(CONFIG_ZYNQ_QSPI) && \
  defined(CONFIG_ZYNQ_GEM_SPI_MAC_OFFSET)

#define CMD_OTPREAD_ARRAY_FAST 0x4b
  struct spi_flash *flash;
	flash = spi_flash_probe(CONFIG_SF_DEFAULT_BUS,
	  CONFIG_SF_DEFAULT_CS,
		CONFIG_SF_DEFAULT_SPEED,
		CONFIG_SF_DEFAULT_MODE
	);
	if(!flash) {
		debug("%s: SPI(bus:%u cs:%u) probe failed.\n", __func__,
			CONFIG_SF_DEFAULT_BUS,
			CONFIG_SF_DEFAULT_CS
		);
		return -ENODEV;
	}
	flash->read_cmd = CMD_OTPREAD_ARRAY_FAST;
	ret = spi_flash_read(flash, CONFIG_ZYNQ_GEM_SPI_MAC_OFFSET, 6, ethaddr);
	if(ret)
		debug("%s: SPI EEPROM MAC address read failed\n", __func__);
	else
		debug("%s: SPI EEPROM MAC %pM\n", __func__, ethaddr);
	if(flash)
		spi_flash_free(flash);
#endif
	return ret;
}
```

追加したdefineを有効にするため  
展開した先の scripts/config_whitelist.txt に追記

```
CONFIG_ZYNQ_GEM_SPI_MAC_OFFSET
```

以上をパッチファイルにする.

```
> git diff > <プロジェクトディレクトリ>/project-spec/meta-user/recipes-bsp/u-boot/files/zyboz7-spi-mac.patch
```

u-bootのユーザ用レシピを編集  
project-spec/meta-user/recipes-bsp/u-boot/u-boot-xlnx_%.bbappend

```
FILESEXTRAPATHS_prepend := "${THISDIR}/files:"

SRC_URI += "file://platform-top.h file://zyboz7-spi-mac.patch"
```

追加したdefine定数を有効にするためのpetalinuxレシピを編集  
project-spec/meta-user/recipes-bsp/u-boot/files/platform-top.h

```
#define CONFIG_ZYNQ_GEM_SPI_MAC_OFFSET 0x20
```

ビルドが通るか試す

```
> petalinux-build -c u-boot
```

petalinuxのu-boot設定でMAC ADDRESSが指定されている場合, そちらが優先されるので設定を変更する.

```
> petalinux-config
```

設定項目は

[Subsystem AUTO Hardware Settings] => [Ethernet Settings] => [Ethernet MAC address]

で、何か記述されている場合は空にしておく.


### 参考URI
- ZYBO用U-Boot で MAC アドレスを SPI ROM から読む(Qiita) https://qiita.com/ikwzm/items/1b79a528634fd2c09e1f

## udmabuf のカスタムモジュールレシピ 追加

meta-users/recipes-modules/udmabuf を作成

```
>petalinux-create -t modules --name udmabuf --enable
```

Linux起動時にメモリを確保するためにデバイスツリーに登録

TODO...

## dropbear(ssh server)の起動時オプション変更

デフォルトでは ssh 経由でのrootログインは禁止されているのでログインできるように変更する.

*) PAM周りの設定がおかしいのか,パスワード入力を試行することでPAMが有効になってログインできてしまうようだが.

*) 別ユーザを追加してキー認証以外のログインを禁止するべきと思う.

起動オプションの制御は/etc/default/dropbear 内の記述で行っているようなのでこれを変更する.

petalinux上ではdropbaerレシピがdropbear.defaultという名前でファイルを提供しているので, これを所望の内容に置き換える.

meta-user/recipes-core に dropbear ディレクトリを作成し, dropbear_2018.%.bbappend を以下の内容で作成する.
ファイルの置き換えのみなので簡単な内容.

```
FILESEXTRAPATHS_prepend := "${THISDIR}/files:"
```

dropbearの元レシピからdropbear.defaultを持ってきて内容を変更し
meta-user/recipes-core/dropbear/files 以下に配置する.
(元レシピの位置は <petalinuxインストール先>/components/yocto/source/arm/layers/core/meta/recipes-core/dropbear )

petalinuxがyocto由来のレシピに対する変更を警告してくるので
dropbearを警告対象から除外するために
meta-user/conf/petalinuxbsp.conf に以下を追記

```
SIGGEN_UNLOCKED_RECIPES += "dropbear"
```

## SDKの作成

プロジェクトルートディレクトリで以下のコマンドを実行
時間がかかるので注意。

```
>petalinux-build -sdk
```

出力は images/linux/sdk.sh  
内容はクロスコンパイル環境のバイナリを抱えたインストール用のシェルスクリプト  

```
>./sdk.sh <インストール先ディレクトリ>
```

でインストール.インストール後は

```
> . <インストール先ディレクトリ>/
```

でクロスコンパイル環境が設定される.  
(CC とか CXX とか LD とか CROSSCOMPILEとかの環境変数)

## C++ でビルドしたバイナリが動作しない

デフォルトの状態だとrootfsにlibstdc++が入っていないため、パッケージを導入する.

```
> petalinux-config -c rootfs
```

[Filesystem Packages] => [misc] => [gcc-runtime] => libstdc++ にチェック


## 備忘

## 課題

- petalinux-config -c u-boot の表示が崩れる
- 起動時に/dev/misc/rtc に対するメッセージが煩いので止める

## 作成したスクリプト

- createBootBin.sh: BOOT.BINを生成します.
