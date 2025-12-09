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

**[English](README.md)** | **Indonesia** | **[中文](README_zh-CN.md)** | **[日本語](README_JP.md)** | **[Русский](README_RU.md)**

</div>

## Ikhtisar

<table>
<tr>
<td width="70%">

**KonaBess Next** adalah evolusi dari alat KonaBess asli, yang direkayasa ulang untuk generasi Snapdragon modern. Ini adalah aplikasi khusus yang dirancang untuk menyesuaikan tabel frekuensi dan voltase GPU, memungkinkan pengguna mencapai kinerja lebih tinggi atau efisiensi energi yang lebih baik tanpa kompilasi ulang kernel.

Aplikasi ini bekerja dengan membongkar image Boot atau Vendor Boot, mendekompilasi dan mengedit file binary device tree (dtb) yang relevan, lalu mengemas ulang dan mem-flash image yang telah dimodifikasi. Ini memberikan pendekatan yang ramping dan bebas kernel untuk overclocking dan undervolting.

</td>
<td width="30%" align="center">

<img src="doc/icon.png" alt="KonaBess Next Logo" width="180"/>

**KonaBess Next**

*GPU Tuner*

</td>
</tr>
</table>

## Apa yang Baru di "Next"?

**KonaBess Next** dibangun di atas warisan alat asli dengan peningkatan arsitektur dan fitur yang signifikan:

*   **Kontrol Voltase Terperinci (Granulated Voltage Control)**: Tidak seperti KonaBess asli yang sering mengandalkan langkah voltase tetap untuk chip lama, **KonaBess Next** memperkenalkan dukungan untuk sistem kontrol voltase berbutir halus yang ditemukan di Snapdragon baru. Ini memungkinkan kurva undervolting/overvolting yang presisi untuk memaksimalkan efisiensi per hertz.
*   **Dukungan Chipset Generasi Berikutnya**: Sepenuhnya diperbarui untuk mendukung platform terbaru termasuk **Snapdragon 8 Elite (Gen 5)**, **Snapdragon 8 Gen 3**, dan **Snapdragon 8 Gen 2**.
*   **Peningkatan Editor**:
    *   **Sistem Undo/Redo**: Bereksperimen dengan aman pada perubahan tabel dengan dukungan riwayat penuh.
    *   **Simpan Otomatis**: Jangan pernah kehilangan penyesuaian konfigurasi Anda.
    *   **Desain Material 3**: Antarmuka pengguna responsif yang sepenuhnya modern.
    *   **Manajemen Sesi**: Mengingat status pengeditan Anda bahkan setelah menutup aplikasi.

## Fitur Utama

*   **Kustomisasi Bebas Kernel**: Edit frekuensi GPU dan tabel voltase tanpa mengkompilasi ulang seluruh kernel.
*   **Optimasi Kinerja**: Overclock chip lama (misalnya Snapdragon 865) untuk menyaingi kinerja generasi baru.
*   **Penyetelan Efisiensi**: Undervolt GPU untuk mengurangi konsumsi daya secara signifikan (misalnya, pengurangan hingga 25% pada SD865).
*   **Kompatibilitas Luas**: Mendukung berbagai chipset Snapdragon dari seri 6 hingga seri 8 terbaru.

## Perangkat yang Didukung

### Seri Snapdragon 8
| Chipset | Model |
|:---|:---|
| **Snapdragon 8 Elite Gen 5** | (Terbaru) |
| **Snapdragon 8s Gen 4** | (Dukungan Terbaru) |
| **Snapdragon 8s Gen 3** | |
| **Snapdragon 8 Gen 3** | |
| **Snapdragon 8 Gen 2** | |
| **Snapdragon 8+ Gen 1** | |
| **Snapdragon 8 Gen 1** | |
| **Snapdragon 888** | |
| **Snapdragon 865** | (Target Asli) |
| **Snapdragon 855** | |

### Seri Snapdragon 7
| Chipset |
|:---|
| **Snapdragon 7+ Gen 3** |
| **Snapdragon 7+ Gen 2** |
| **Snapdragon 7 Gen 1** |
| **Snapdragon 780G** |
| **Snapdragon 778G** |
| **Snapdragon 765** |
| **Snapdragon 750** |

### Seri Snapdragon 6
| Chipset |
|:---|
| **Snapdragon 690** |

## Prasyarat

*   **OS Android**: Android 9.0 atau lebih tinggi.
*   **Akses Root**: Magisk, KernelSU, atau APatch **wajib**.
*   **Bootloader Tidak Terkunci**: Diperlukan untuk mem-flash image boot yang dimodifikasi.

## Instruksi Penggunaan

1.  **Cadangan**: Selalu buat cadangan image Boot/Vendor Boot Anda saat ini sebelum melanjutkan.
2.  **Impor**: Gunakan aplikasi untuk mengimpor image Anda saat ini.
3.  **Edit**: Ubah nilai frekuensi dan voltase sesuai keinginan.
4.  **Repack & Flash**: Aplikasi akan menangani pengemasan ulang. Flash image yang dimodifikasi ke perangkat Anda.

*Lihat bagian "Bantuan" dalam aplikasi untuk panduan langkah demi langkah yang mendetail.*

## Mengapa "KonaBess"?

*   **Warisan**: "Kona" adalah nama kode untuk platform Snapdragon 865.
*   **Tujuan**: Dibuat untuk mengatasi masalah efisiensi energi Snapdragon 888 dengan memaksimalkan potensi Snapdragon 865.
*   **Kontinuitas**: Nama tetap sebagai penghormatan kepada asalnya, meskipun memperluas dukungan ke generasi yang lebih baru dan lebih lama.

## Binary Prebuilt

Proyek ini menggunakan alat berikut:
*   [magiskboot](https://github.com/topjohnwu/Magisk)
*   [dtc](https://github.com/xzr467706992/dtc-aosp/tree/standalone)

## Tangkapan Layar

<div align="center">
  <img src="doc/screenshots/ss1.jpg" width="30%" alt="Screenshot 1" />
  <img src="doc/screenshots/ss2.jpg" width="30%" alt="Screenshot 2" />
  <img src="doc/screenshots/ss3.jpg" width="30%" alt="Screenshot 3" />
</div>

## Kredit

*   **KonaBess Asli**: [libxzr](https://github.com/libxzr) - Untuk ide orisinal dan fondasi KonaBess.
*   **Magisk**: [topjohnwu](https://github.com/topjohnwu) - Untuk `magiskboot` dan suite Magisk.
*   **DTC**: [libxzr](https://github.com/libxzr/dtc-aosp) - Untuk Device Tree Compiler.

## Penafian

**Memodifikasi file sistem dan overclocking/undervolting melibatkan risiko yang melekat.**

Pengembang tidak bertanggung jawab atas kerusakan pada perangkat Anda, kehilangan data, atau ketidakstabilan yang diakibatkan oleh penggunaan aplikasi ini. Lanjutkan dengan hati-hati dan risiko Anda sendiri.
