# WaterReminder / 喝水提醒

**English** | A water-drinking reminder app for Android, helping you build a healthy hydration habit with scheduled reminders, drink logging, and streak tracking.

**中文** | 一款 Android 喝水提醒应用，通过定时提醒、饮水记录和达标追踪，帮助你养成健康饮水习惯。

---

## Screenshots / 截图

| Home / 首页 | Records / 记录 | Reminders / 提醒 | Person Types / 人群类型 |
|:---:|:---:|:---:|:---:|
| Water wave progress + quick drink | Daily & monthly summary | Custom reminder times | Manage target & interval |

---

## Features / 功能介绍

- **Water Wave Progress / 水波进度球** — Custom animated circular progress showing today's intake vs daily goal / 自定义水波进度球，展示今日饮水量 vs 每日目标
- **Quick Drink / 快捷打卡** — One-tap logging with preset amounts (150/200/250/500ml) or custom input / 一键打卡，预设 150/200/250/500ml 或自定义输入
- **Three Reminder Modes / 三种提醒机制**
  - Interval reminder — based on person type config / 间隔提醒（基于人群类型配置）
  - Mini-cycle nag — re-reminds every 5 min if not logged / 小周期催促（未打卡时每 5 分钟再次提醒）
  - Fixed-time reminders — custom daily alarms with labels / 固定时间提醒（可自定义标签）
- **Daily & Monthly Records / 日/月饮水记录** — View, delete, and check daily goal completion / 查看、删除记录，检查每日达标状态
- **Person Types / 人群类型** — Predefined types (Adult Male, Adult Female, Teen, Athlete) + custom types with personalized goals, intervals, and notification windows / 预置 4 种人群 + 自定义类型，可配置每日目标、提醒间隔、通知时段
- **Streak Tracking / 连续达标天数** — Consecutive days reaching daily goal / 从今天起连续达标天数
- **Background Persistence / 后台保活** — Foreground service + battery optimization bypass + vendor-specific auto-start guidance (Xiaomi/Huawei/OPPO/vivo/Samsung) / 前台服务 + 电池优化白名单 + 各厂商自启动引导
- **Boot Recovery / 开机自启** — Automatically reschedules all reminders after device reboot or app update / 开机或应用更新后自动恢复所有提醒
- **Multi-language / 多语言** — UI in Chinese; codebase in English / 界面中文，代码英文

---

## Tech Stack / 技术栈

| Category / 类别 | Technology / 技术 |
|:---|:---|
| Language | Kotlin |
| Min SDK / 最低版本 | 24 (Android 7.0) |
| Target SDK / 目标版本 | 34 (Android 14) |
| Architecture / 架构 | MVVM (ViewModel + LiveData) |
| Database / 数据库 | Room |
| Async / 异步 | Kotlin Coroutines |
| Charts / 图表 | MPAndroidChart |
| Animation / 动画 | Lottie, Canvas custom view |
| Alarm / 闹钟 | AlarmManager (setExact + setAlarmClock) |

---

## Build / 编译

```powershell
$env:JAVA_HOME = "D:\jdk\jdk-17.0.6"
$env:ANDROID_HOME = "D:\a-sdk"
.\gradlew.bat assembleDebug
```

Requirements: JDK 17+, Android SDK 34 / 需要 JDK 17+, Android SDK 34

---

## License / 许可证

MIT License
