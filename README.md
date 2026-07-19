# 🦥 懒人浏览器 (LazyBrowser)

极简轻量 Android 浏览器，专为懒人设计。

## ✨ 核心特性

### 🏠 懒人首页
- **继续阅读**：上次看的书一打开就能继续
- **快捷站点**：12个常用网站一点就开
- **一键搜小说**：输入书名，同时搜8个小说站
- **发现好书**：按分类推荐热门小说

### 📖 小说阅读器
- **自动识别**：打开小说站自动进入阅读模式
- **沉浸阅读**：全屏无干扰，轻触唤出工具栏
- **8种主题**：护眼淡黄、纯白、羊皮纸、暮光紫...
- **进度记忆**：自动保存阅读位置，下次继续
- **章节切换**：目录/上一章/下一章

### 📚 书架
- **3列网格**：封面+书名，一目了然
- **列表视图**：可切换，显示进度条
- **继续阅读卡片**：最近读的书置顶
- **长按批量操作**

### 📥 下载管理
- **缓存全本**：一键离线阅读
- **实时进度**：下载中显示百分比
- **已完成**：关联书架，点击即读
- **批量删除/导出**

### ⚡ 其他
- **广告拦截**：内置域名黑名单 + EasyList
- **多搜索引擎**：Google/百度/Bing/DDG/搜狗
- **夜间模式**：一键切换
- **阅读模式**：提取正文，去除杂乱
- **页面内查找**
- **分享页面**

## 🛠️ 技术栈

- Kotlin + Android WebView
- Material Design 3
- Room 数据库（书签/历史）
- MVVM 架构
- Gradle Kotlin DSL

## 📦 构建方式

### 方式一：GitHub Actions（推荐）

1. Fork 或上传代码到 GitHub
2. Actions 会自动触发编译
3. 等 3-5 分钟
4. 去 Actions → Artifacts → 下载 APK

### 方式二：Android Studio

1. 下载 [Android Studio](https://developer.android.com/studio)
2. 打开项目文件夹
3. 等待 Gradle 同步完成
4. 点击 Run ▶️

### 方式三：命令行

```bash
# 需要先安装 JDK 17 和 Android SDK
chmod +x gradlew
./gradlew assembleDebug
# APK 输出: app/build/outputs/apk/debug/app-debug.apk
```

## 📁 项目结构

```
LazyBrowser/
├── app/src/main/java/com/lazybrowser/app/
│   ├── MainActivity.kt          # 主界面
│   ├── adblock/AdBlocker.kt     # 广告拦截
│   ├── bookshelf/               # 书架模块
│   ├── data/                    # 数据库
│   ├── download/                # 下载管理
│   ├── home/                    # 懒人首页
│   ├── reader/                  # 小说阅读器
│   ├── settings/                # 设置
│   └── tab/                     # 标签页管理
└── app/src/main/res/
    ├── layout/                  # 界面布局
    ├── drawable/                # 图标和样式
    └── menu/                    # 菜单
```

## 📱 最低要求

- Android 7.0 (API 24)
- APK 大小 < 2MB

## 📄 License

MIT
