#!/bin/bash

# ZZULI 教学评价自动化工具 - 启动脚本
# Author: ShiYi

echo "正在编译并运行 ZZULI 教学评价助手..."
echo ""

# 检查是否传入 --debug 参数
if [[ "$*" == *"--debug"* ]] || [[ "$*" == *"-d"* ]]; then
    echo "✓ DEBUG模式已启用"
    # 使用 DEBUG 模式的 logback 配置
    export JAVA_OPTS="-Dlogback.configurationFile=logback-debug.xml"
    ./gradlew run
else
    # 使用默认的 INFO 级别
    ./gradlew run
fi

