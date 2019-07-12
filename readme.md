# ZyboZ7-plnx

ZyboZ7-20で使用するPetaLinux環境の構築 試行記録  
作業していて、思ったことを書き連ねています.

## TODOリスト

- [X] USB(ホストモード) を利用可能にする
- [X] Ethernetを利用可能にする 
- [ ] udmabuf のカスタムモジュールレシピ 追加
- [ ] uboot にZyboZ7-20用のMACアドレス読み込みパッチ 追加
- [ ] SDKの作成
- [ ] telnetd, ftpd の無効化

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

### 参考URI

- Ubuntu+ZynqでUSB-HOSTが機能しないときの解決方法(Qiita): https://qiita.com/lp6m/items/45d90e9d2443c2198e96

## Ethernetを利用可能にする

特になにもしなくてよい.
vivado 側での設定になるが、2018.3辺りまでのDiglintのボード設定ファイルだとETH0のMIO接続がEMIOになっていたりするので
そちらを確認.
(ただしこのままだとMACアドレスは手動で書き換える必要あり)

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

## 作成したスクリプト

- createBootBin.sh: BOOT.BINを生成します.
