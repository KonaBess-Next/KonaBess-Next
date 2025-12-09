# KonaBess Next

![Banner](https://capsule-render.vercel.app/api?type=waving&color=FF3333&height=220&section=header&text=KonaBess%20Next&fontSize=70&fontColor=ffffff&desc=Snapdragon%20GPU%20Frequency%20&%20Voltage%20Tuner&descAlignY=65&descAlign=50)

<div align="center">

[![GitHub stars](https://img.shields.io/github/stars/ireddragonicy/KonaBess?style=for-the-badge&color=FAB005)](https://github.com/ireddragonicy/KonaBess/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/ireddragonicy/KonaBess?style=for-the-badge&color=42BE65)](https://github.com/ireddragonicy/KonaBess/network/members)
[![GitHub issues](https://img.shields.io/github/issues/ireddragonicy/KonaBess?style=for-the-badge&color=FA5252)](https://github.com/ireddragonicy/KonaBess/issues)
[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android)](https://www.android.com)
[![Java](https://img.shields.io/badge/Language-Java-007396?style=for-the-badge&logo=java&logoColor=white)](https://www.java.com)
[![Root Required](https://img.shields.io/badge/Root-Required-critical?style=for-the-badge)](https://github.com/topjohnwu/Magisk)
[![License](https://img.shields.io/github/license/ireddragonicy/KonaBess?style=for-the-badge&color=228BE6)](LICENSE)

**[English](README.md)** | **[Indonesia](README_ID.md)** | **中文** | **[日本語](README_JP.md)** | **[Русский](README_RU.md)**

</div>

## 概述

<table>
<tr>
<td width="70%">

**KonaBess Next** 是原版 KonaBess 工具的进化版，专为现代骁龙 (Snapdragon) 平台重新设计。这是一款专门用于自定义骁龙设备 GPU 频率和电压表的应用程序，允许用户在无需重新编译内核的情况下直接修改这些参数，从而获得更高的性能或更好的能效。

该应用程序的工作原理是解包 Boot 或 Vendor Boot 镜像，反编译并编辑相关的设备树二进制 (dtb) 文件，然后重新打包并刷入修改后的镜像。这通过一种精简的、无需内核源码的方式，实现了超频和欠压。

</td>
<td width="30%" align="center">

<img src="doc/icon.png" alt="KonaBess Next Logo" width="180"/>

**KonaBess Next**

*GPU 调校工具*

</td>
</tr>
</table>

## "Next" 版本新特性

**KonaBess Next** 建立在原版工具的基础上，进行了重大的架构和功能升级：

*   **精细化电压控制 (Granulated Voltage Control)**: 与原版 KonaBess 通常依赖旧芯片的固定电压步进不同，**KonaBess Next** 引入了对新骁龙芯片中精细电压控制系统的支持。这允许进行精确的欠压/超频曲线调整，以最大化每赫兹的能效。
*   **新一代芯片支持**: 全面更新以支持最新平台，包括 **Snapdragon 8 Elite (Gen 5)**, **Snapdragon 8 Gen 3**, 和 **Snapdragon 8 Gen 2**。
*   **编辑器改进**:
    *   **撤销/重做系统**: 拥有完整的历史记录支持，可以安全地尝试修改频率表。
    *   **自动保存**: 不再丢失您的配置调整。
    *   **Material 3 设计**: 完全现代化的响应式用户界面。
    *   **会话管理**: 即使关闭应用后也能记住您的编辑状态。

## 主要功能

*   **无需内核代码**: 修改 GPU 频率和电压表无需重新编译整个内核。
*   **性能优化**: 超频旧芯片（如骁龙 865）以媲美新一代芯片的性能。
*   **能效调优**: 对 GPU 进行欠压降频，显著降低功耗（例如：在 SD865 上最高可降低 25%）。
*   **广泛的兼容性**: 支持从骁龙 6 系列到最新 8 系列的各种芯片组。

## 支持设备

### 骁龙 8 系列 (Snapdragon 8 Series)
| 芯片组 | 型号 |
|:---|:---|
| **Snapdragon 8 Elite Gen 5** | (最新) |
| **Snapdragon 8s Gen 4** | (最新支持) |
| **Snapdragon 8s Gen 3** | |
| **Snapdragon 8 Gen 3** | |
| **Snapdragon 8 Gen 2** | |
| **Snapdragon 8+ Gen 1** | |
| **Snapdragon 8 Gen 1** | |
| **Snapdragon 888** | |
| **Snapdragon 865** | (最初目标) |
| **Snapdragon 855** | |

### 骁龙 7 系列 (Snapdragon 7 Series)
| 芯片组 |
|:---|
| **Snapdragon 7+ Gen 3** |
| **Snapdragon 7+ Gen 2** |
| **Snapdragon 7 Gen 1** |
| **Snapdragon 780G** |
| **Snapdragon 778G** |
| **Snapdragon 765** |
| **Snapdragon 750** |

### 骁龙 6 系列 (Snapdragon 6 Series)
| 芯片组 |
|:---|
| **Snapdragon 690** |

##前提条件

*   **Android 系统**: Android 9.0 或更高版本。
*   **Root 权限**: 必须拥有 Magisk, KernelSU, 或 APatch。
*   **已解锁 Bootloader**: 刷入修改后的 boot 镜像所必需。

## 使用说明

1.  **备份**: 在继续操作之前，请务必备份当前的 Boot/Vendor Boot 镜像。
2.  **导入**: 使用应用程序导入当前的镜像。
3.  **编辑**: 根据需要修改频率和电压值。
4.  **重打包与刷入**: 应用程序将处理重打包过程。将修改后的镜像刷入您的设备。

*请参阅应用程序内的“帮助”部分以获取详细的分步指导。*

## 为什么叫 "KonaBess"?

*   **致敬经典**: "Kona" 是骁龙 865 平台的代号。
*   **初衷**: 最初是为了解决骁龙 888 能效不佳的问题，通过挖掘骁龙 865 的潜力来超越它。
*   **传承**: 尽管支持范围已扩展到更新和更旧的世代，但为了致敬其起源，名称得以保留。

## 预构建二进制文件

本项目使用了以下工具：
*   [magiskboot](https://github.com/topjohnwu/Magisk)
*   [dtc](https://github.com/xzr467706992/dtc-aosp/tree/standalone)

## 截图

<div align="center">
  <img src="doc/screenshots/ss1.jpg" width="30%" alt="Screenshot 1" />
  <img src="doc/screenshots/ss2.jpg" width="30%" alt="Screenshot 2" />
  <img src="doc/screenshots/ss3.jpg" width="30%" alt="Screenshot 3" />
</div>

## 致谢

*   **KonaBess 原作者**: [libxzr](https://github.com/libxzr) - 感谢 KonaBess 的原始创意和基础。
*   **Magisk**: [topjohnwu](https://github.com/topjohnwu) - 感谢 `magiskboot` 和 Magisk 套件。
*   **DTC**: [libxzr](https://github.com/libxzr/dtc-aosp) - 感谢设备树编译器 (Device Tree Compiler)。

## 免责声明

**修改系统文件和超频/欠压涉及固有风险。**

开发者对因使用本应用程序而导致的设备损坏、数据丢失或系统不稳定不承担任何责任。请谨慎操作，后果自负。
