# OpenHands Frontend for Cross-Platform

这是一个面向 OpenHands 的跨平台前端项目。当前暂提供 Android 客户端，未来将支持 Intel Mac 平台提供统一的 OpenHands 使用入口。

## 部署

1. 准备可访问的 OpenHands Agent Server （部署时需要使用API KEY指令或Config方式先设置后端登录KEY）
2. 在客户端中填写 Agent Server 的 Base URL 和 API Key。
3. Android 客户端可使用项目中的 Gradle 配置构建：

   ```bash
   cd android
   ./gradlew assembleRelease
   ```

4. 将生成的 Release APK 安装到 Android 设备后即可使用。

## 版权

Copyright © OpenHands 。
