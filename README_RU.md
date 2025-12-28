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

**[English](README.md)** | **[Indonesia](README_ID.md)** | **[中文](README_zh-CN.md)** | **[日本語](README_JP.md)** | **Русский**

</div>

## Обзор

<table>
<tr>
<td width="70%">

**KonaBess Next** — это эволюция оригинального инструмента KonaBess, переработанная для современных поколений Snapdragon. Это специализированное приложение, предназначенное для настройки таблиц частот и напряжений GPU, позволяющее пользователям достигать более высокой производительности или лучшей энергоэффективности без перекомпиляции ядра.

Приложение работает путем распаковки образа Boot или Vendor Boot, декомпиляции и редактирования соответствующих бинарных файлов дерева устройств (dtb), а затем перепаковки и прошивки модифицированного образа. Это обеспечивает простой метод разгона и андервольтинга без внесения изменений в ядро.

</td>
<td width="30%" align="center">

<img src="doc/icon.png" alt="KonaBess Next Logo" width="180"/>

**KonaBess Next**

*Тюнер GPU*

</td>
</tr>
</table>

## Что нового в "Next"?

**KonaBess Next** основывается на наследии оригинального инструмента со значительными архитектурными и функциональными улучшениями:

*   **Гранулированный контроль напряжения (Granulated Voltage Control)**: В отличие от оригинального KonaBess, который часто полагался на фиксированные шаги напряжения для старых чипов, **KonaBess Next** вводит поддержку систем тонкого контроля напряжения, найденных в новых процессорах Snapdragon. Это позволяет точно настраивать кривые андервольтинга/разгона для максимизации эффективности на герц.
*   **Поддержка чипсетов нового поколения**: Полностью обновлено для поддержки новейших платформ, включая **Snapdragon 8 Elite (Gen 5)**, **Snapdragon 8 Gen 3** и **Snapdragon 8 Gen 2**.
*   **Улучшения редактора**:
    *   **Система отмены/повтора**: Безопасно экспериментируйте с изменениями таблиц с полной поддержкой истории.
    *   **Автосохранение**: Никогда не теряйте свои настройки конфигурации.
    *   **Material 3 Design**: Полностью современный, отзывчивый пользовательский интерфейс.
    *   **Управление сеансами**: Запоминает состояние редактирования даже после закрытия приложения.

## Ключевые особенности

*   **Настройка без ядра**: Редактируйте таблицы частот и напряжений GPU без перекомпиляции всего ядра.
*   **Оптимизация производительности**: Разгоняйте старые чипы (например, Snapdragon 865), чтобы соперничать с производительностью новых поколений.
*   **Настройка эффективности**: Андервольтинг GPU для значительного снижения энергопотребления (например, до 25% снижения на SD865).
*   **Широкая совместимость**: Поддержка широкого спектра чипсетов Snapdragon от 6-й серии до новейшей 8-й серии.

## Поддерживаемые устройства

### Snapdragon 8 Series
| Чипсет | Модель |
|:---|:---|
| **Snapdragon 8 Elite Gen 5** | (Новейший) |
| **Snapdragon 8s Gen 4** | (Последняя поддержка) |
| **Snapdragon 8s Gen 3** | |
| **Snapdragon 8 Gen 3** | |
| **Snapdragon 8 Gen 2** | |
| **Snapdragon 8+ Gen 1** | |
| **Snapdragon 8 Gen 1** | |
| **Snapdragon 888** | |
| **Snapdragon 865** | (Оригинальная цель) |
| **Snapdragon 855** | |

### Snapdragon 7 Series
| Чипсет |
|:---|
| **Snapdragon 7+ Gen 3** |
| **Snapdragon 7+ Gen 2** |
| **Snapdragon 7 Gen 1** |
| **Snapdragon 780G** |
| **Snapdragon 778G** |
| **Snapdragon 765** |
| **Snapdragon 750** |

### Snapdragon 6 Series
| Чипсет |
|:---|
| **Snapdragon 690** |

## Предварительные требования

*   **ОС Android**: Android 9.0 или выше.
*   **Root-доступ**: Magisk, KernelSU или APatch **обязательны**.
*   **Разблокированный загрузчик**: Необходим для прошивки модифицированных загрузочных образов.

## Инструкции по использованию

1.  **Резервное копирование**: Всегда делайте резервную копию вашего текущего образа Boot/Vendor Boot перед продолжением.
2.  **Импорт**: Используйте приложение для импорта вашего текущего образа.
3.  **Редактирование**: Измените значения частоты и напряжения по желанию.
4.  **Перепаковка и прошивка**: Приложение обработает перепаковку. Прошейте модифицированный образ на ваше устройство.

*Обратитесь к разделу «Помощь» в приложении для получения подробных пошаговых инструкций.*

## Почему "KonaBess"?

*   **Наследие**: "Kona" — это кодовое имя платформы Snapdragon 865.
*   **Цель**: Создан для решения проблем энергоэффективности Snapdragon 888 путем максимизации потенциала Snapdragon 865.
*   **Преемственность**: Имя остается как дань уважения его истокам, несмотря на расширение поддержки на новые и старые поколения.

## Встроенные бинарные файлы

Этот проект использует следующие инструменты:
*   [magiskboot](https://github.com/topjohnwu/Magisk)
*   [dtc](https://github.com/xzr467706992/dtc-aosp/tree/standalone)

## Скриншоты

<div align="center">
  <img src="doc/screenshots/ss1.jpg" width="30%" alt="Скриншот 1" />
  <img src="doc/screenshots/ss2.jpg" width="30%" alt="Скриншот 2" />
  <img src="doc/screenshots/ss3.jpg" width="30%" alt="Скриншот 3" />
</div>

## Благодарности

*   **Оригинальный KonaBess**: [libxzr](https://github.com/libxzr) - За оригинальную идею и основу KonaBess.
*   **Magisk**: [topjohnwu](https://github.com/topjohnwu) - За `magiskboot` и набор инструментов Magisk.
*   **DTC**: [libxzr](https://github.com/libxzr/dtc-aosp) - За компилятор дерева устройств (Device Tree Compiler).

## Отказ от ответственности

**Модификация системных файлов и разгон/андервольтинг сопряжены с неотъемлемыми рисками.**

Разработчик не несет ответственности за любой ущерб вашему устройству, потерю данных или нестабильность, возникшие в результате использования этого приложения. Продолжайте с осторожностью и на свой страх и риск.
