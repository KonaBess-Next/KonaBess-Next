# KonaBess Next

![Banner](https://capsule-render.vercel.app/api?type=waving&color=FF3333&height=220&section=header&text=KonaBess%20Next&fontSize=70&fontColor=ffffff&desc=Snapdragon%20GPU%20Frequency%20&%20Voltage%20Tuner&descAlignY=65&descAlign=50)

<div align="center">

[![GitHub stars](https://img.shields.io/github/stars/KonaBess-Next/KonaBess-Next?style=for-the-badge&color=FAB005)](https://github.com/KonaBess-Next/KonaBess-Next/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/KonaBess-Next/KonaBess-Next?style=for-the-badge&color=42BE65)](https://github.com/KonaBess-Next/KonaBess-Next/network/members)
[![GitHub issues](https://img.shields.io/github/issues/KonaBess-Next/KonaBess-Next?style=for-the-badge&color=FA5252)](https://github.com/KonaBess-Next/KonaBess-Next/issues)
[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android)](https://www.android.com)
[![Java](https://img.shields.io/badge/Language-Java-007396?style=for-the-badge&logo=java&logoColor=white)](https://www.java.com)
[![Root Required](https://img.shields.io/badge/Root-Required-critical?style=for-the-badge)](https://github.com/topjohnwu/Magisk)
[![License](https://img.shields.io/github/license/KonaBess-Next/KonaBess-Next?style=for-the-badge&color=228BE6)](LICENSE)

**[English](README.md)** | **[Indonesia](README_ID.md)** | **[中文](README_zh-CN.md)** | **日本語** | **[Русский](README_RU.md)**

</div>

## 概要

<table>
<tr>
<td width="70%">

**KonaBess Next** は、オリジナルの KonaBess ツールの進化版であり、最新の Snapdragon 世代向けに再設計されました。これは、GPU の周波数と電圧テーブルをカスタマイズするために設計された専用アプリケーションであり、カーネルを再コンパイルすることなく、ユーザーがこれらのパラメータを直接変更して、より高いパフォーマンスや優れたエネルギー効率を実現できるようにします。

アプリケーションは、Boot または Vendor Boot イメージを展開し、関連するデバイスツリーバイナリ (dtb) ファイルをデコンパイルして編集し、変更されたイメージを再パックしてフラッシュすることで動作します。これにより、カーネルフリーで合理化されたオーバークロックとアンダーボルティングのアプローチが提供されます。

</td>
<td width="30%" align="center">

<img src="doc/icon.png" alt="KonaBess Next Logo" width="180"/>

**KonaBess Next**

*GPU チューナー*

</td>
</tr>
</table>

## "Next" の新機能

**KonaBess Next** は、オリジナルのツールの遺産に基づいて構築されており、アーキテクチャと機能が大幅にアップグレードされています：

*   **詳細な電圧制御 (Granulated Voltage Control)**: 古いチップ向けの固定電圧ステップに依存することが多かったオリジナルの KonaBess とは異なり、**KonaBess Next** は、新しい Snapdragon に搭載されている微細な電圧制御システムのサポートを導入しました。これにより、1ヘルツあたりの効率を最大化するための正確なアンダーボルト/オーバーボルト曲線の調整が可能になります。
*   **次世代チップセットのサポート**: **Snapdragon 8 Elite (Gen 5)**、**Snapdragon 8 Gen 3**、**Snapdragon 8 Gen 2** などの最新プラットフォームをサポートするために完全に更新されました。
*   **エディタの改善**:
    *   **元に戻す/やり直す (Undo/Redo) システム**: 完全な履歴サポートにより、テーブルの変更を安全に試すことができます。
    *   **自動保存**: 設定の調整を失うことはありません。
    *   **Material 3 デザイン**: 完全にモダンでレスポンシブなユーザーインターフェース。
    *   **セッション管理**: アプリを閉じた後でも編集状態を記憶します。

## 主な機能

*   **カーネルフリーのカスタマイズ**: カーネル全体を再コンパイルすることなく、GPU の周波数と電圧テーブルを編集できます。
*   **パフォーマンスの最適化**: 古いチップ（例：Snapdragon 865）をオーバークロックして、新しい世代のパフォーマンスに匹敵させます。
*   **効率のチューニング**: GPU をアンダーボルトして、消費電力を大幅に削減します（例：SD865 で最大 25% 削減）。
*   **幅広い互換性**: 6 シリーズから最新の 8 シリーズまで、幅広い Snapdragon チップセットをサポートしています。

## サポートされているデバイス

### サポートされているチップセット (Supported Chipsets)
| シリーズ | サポートされているモデル |
|:---|:---|
| **Snapdragon 8** | 8 Elite Gen 5, 8 Elite, 8s Gen 4, 8s Gen 3, 8 Gen 3, 8 Gen 2, 8+ Gen 1, 8 Gen 1, 888, 865, 855 |
| **Snapdragon 7** | 7+ Gen 3, 7+ Gen 2, 7 Gen 1, 780G, 778G, 765, 750 |
| **Snapdragon 6** | 690 |

## 前提条件

*   **Android OS**: Android 9.0 以降。
*   **Root アクセス**: Magisk、KernelSU、または APatch が**必須**です。
*   **ブートローダーのアンロック**: 変更されたブートイメージをフラッシュするために必要です。

## 使用方法

1.  **バックアップ**: 続行する前に、必ず現在の Boot/Vendor Boot イメージをバックアップしてください。
2.  **インポート**: アプリを使用して現在のイメージをインポートします。
3.  **編集**: 好みに応じて周波数と電圧の値を変更します。
4.  **再パックとフラッシュ**: アプリが再パック処理を行います。変更されたイメージをデバイスにフラッシュします。

*詳細なステップバイステップガイドについては、アプリ内の「ヘルプ」セクションを参照してください。*

## なぜ "KonaBess" なのか？

*   **遺産**: "Kona" は Snapdragon 865 プラットフォームのコードネームです。
*   **目的**: Snapdragon 865 の可能性を最大限に引き出すことで、Snapdragon 888 のエネルギー効率の懸念に対処するために作成されました。
*   **継続性**: 新旧世代へのサポートを拡大していますが、その起源への敬意を表して名前は残されています。

## プリビルドバイナリ

このプロジェクトでは、以下のツールを使用しています：
*   [magiskboot](https://github.com/topjohnwu/Magisk)
*   [dtc](https://github.com/xzr467706992/dtc-aosp/tree/standalone)

## スクリーンショット

<div align="center">
  <img src="doc/screenshots/ss1.jpg" width="30%" alt="Screenshot 1" />
  <img src="doc/screenshots/ss2.jpg" width="30%" alt="Screenshot 2" />
  <img src="doc/screenshots/ss3.jpg" width="30%" alt="Screenshot 3" />
</div>

## クレジット

*   **KonaBess オリジナル作者**: [libxzr](https://github.com/libxzr) - KonaBess のオリジナルのアイデアと基盤に対して。
*   **Magisk**: [topjohnwu](https://github.com/topjohnwu) - `magiskboot` と Magisk スイートに対して。
*   **DTC**: [libxzr](https://github.com/libxzr/dtc-aosp) - デバイスツリーコンパイラ (Device Tree Compiler) に対して。

## 免責事項

**システムファイルの変更やオーバークロック/アンダーボルティングには、固有のリスクが伴います。**

開発者は、本アプリケーションの使用に起因するデバイスの損傷、データの損失、または不安定性について一切の責任を負いません。注意して、自己責任で行ってください。
