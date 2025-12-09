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

**English** | **[Indonesia](README_ID.md)** | **[中文](README_zh-CN.md)** | **[日本語](README_JP.md)** | **[Русский](README_RU.md)**

</div>

## Overview

<table>
<tr>
<td width="70%">

**KonaBess Next** is the evolution of the original KonaBess tool, re-engineered for modern Snapdragon generations. It is a specialized application designed to customize GPU frequency and voltage tables, allowing users to achieve higher performance or better energy efficiency without kernel recompilation.

The application operates by unpacking the Boot or Vendor Boot image, decompiling and editing the relevant device tree binary (dtb) files, and then repackaging and flashing the modified image. This provides a streamlined, kernel-free approach to overclocking and undervolting.

</td>
<td width="30%" align="center">

<img src="doc/icon.png" alt="KonaBess Next Logo" width="180"/>

**KonaBess Next**

*GPU Tuner*

</td>
</tr>
</table>

## What's New in "Next"?

**KonaBess Next** builds upon the legacy of the original tool with significant architectural and feature upgrades:

*   **Granulated Voltage Control**: Unlike the original KonaBess which often relied on fixed voltage steps for older chips, **KonaBess Next** introduces support for the fine-grained voltage control systems found in newer Snapdragons. This allows for precise undervolting/overvolting curves to maximize efficiency per hertz.
*   **Next-Gen Chipset Support**: Fully updated to support the latest platforms including **Snapdragon 8 Elite (Gen 5)**, **Snapdragon 8 Gen 3**, and **Snapdragon 8 Gen 2**.
*   **Editor Improvements**:
    *   **Undo/Redo System**: Safely experiment with table changes with full history support.
    *   **Auto-Save**: Never lose your configuration adjustments.
    *   **Material 3 Design**: A completely modern, responsive user interface.
    *   **Session Management**: Remembers your editing state even after closing the app.

## Key Features

*   **Kernel-Free Customization**: Edit GPU frequency and voltage tables without recompiling the entire kernel.
*   **Performance Optimization**: Overclock older chips to rival newer generation performance.
*   **Efficiency Tuning**: Undervolt GPU to significantly reduce power consumption (e.g., up to 25% reduction).
*   **Broad Compatibility**: Supports a wide range of Snapdragon chipsets from 6-series to the latest 8-series.

## Supported Devices

### Snapdragon 8 Series
| Chipset | Model |
|:---|:---|
| **Snapdragon 8 Elite Gen 5** | (Newest) |
| **Snapdragon 8s Gen 4** | (Latest Support) |
| **Snapdragon 8s Gen 3** | |
| **Snapdragon 8 Gen 3** | |
| **Snapdragon 8 Gen 2** | |
| **Snapdragon 8+ Gen 1** | |
| **Snapdragon 8 Gen 1** | |
| **Snapdragon 888** | |
| **Snapdragon 865** | (Original Target) |
| **Snapdragon 855** | |

### Snapdragon 7 Series
| Chipset |
|:---|
| **Snapdragon 7+ Gen 3** |
| **Snapdragon 7+ Gen 2** |
| **Snapdragon 7 Gen 1** |
| **Snapdragon 780G** |
| **Snapdragon 778G** |
| **Snapdragon 765** |
| **Snapdragon 750** |

### Snapdragon 6 Series
| Chipset |
|:---|
| **Snapdragon 690** |

## Prerequisites

*   **Android OS**: Android 9.0 or higher.
*   **Root Access**: Magisk, KernelSU, or APatch is **mandatory**.
*   **Unlocked Bootloader**: Necessary for flashing modified boot images.

## Usage Instructions

1.  **Backup**: Always backup your current Boot/Vendor Boot image before proceeding.
2.  **Import**: Use the app to import your current image.
3.  **Edit**: Modify the frequency and voltage values as desired.
4.  **Repack & Flash**: The app will handle the repacking. Flash the modified image to your device.

*Refer to the in-app "Help" section for detailed, step-by-step guidance.*

## Why "KonaBess"?

*   **Legacy**: "Kona" is the codename for the Snapdragon 865 platform.
*   **Purpose**: Created to address the energy efficiency concerns of the Snapdragon 888 by maximizing the potential of the Snapdragon 865.
*   **Continuity**: The name remains as a tribute to its origins, despite expanding support to newer and older generations.

## Prebuilt Binaries

This project utilizes the following tools:
*   [magiskboot](https://github.com/topjohnwu/Magisk)
*   [dtc](https://github.com/xzr467706992/dtc-aosp/tree/standalone)

## Screenshots

<div align="center">
  <img src="doc/screenshots/ss1.jpg" width="30%" alt="Screenshot 1" />
  <img src="doc/screenshots/ss2.jpg" width="30%" alt="Screenshot 2" />
  <img src="doc/screenshots/ss3.jpg" width="30%" alt="Screenshot 3" />
</div>

## Credits

*   **Original KonaBess**: [libxzr](https://github.com/libxzr) - For the original idea and foundation of KonaBess.
*   **Magisk**: [topjohnwu](https://github.com/topjohnwu) - For `magiskboot` and the Magisk suite.
*   **DTC**: [libxzr](https://github.com/libxzr/dtc-aosp) - For the Device Tree Compiler.

## Disclaimer

**Modifying system files and overclocking/undervolting involves inherent risks.**

The developer is not responsible for any damage to your device, data loss, or instability resulting from the use of this application. Proceed with caution and at your own risk.

