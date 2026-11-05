#!/usr/bin/env python3
"""从同一组原创几何生成 SVG、关于页和 adaptive icon，避免多份图形漂移。"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DRAWABLE = ROOT / "app/src/main/res/drawable"
BRAND = ROOT / "docs/brand"
# 主体限制在 adaptive icon 中央 66dp 安全圆内，保留系统裁切与视差余量。
SUN = "M79,43A14,14 0,1 1,51,43A14,14 0,1 1,79,43Z"
CLOUD = (
    "M36,72C27,72 23,66 23,60C23,53 28,48 35,48"
    "C37,39 43,34 51,34C62,34 69,42 69,51"
    "C77,50 84,55 84,62C84,68 79,72 72,72Z"
)
BREEZE = "M39,79H60C65,79 68,77 70,74"
RAYS = "M65,21V25M82,27L79,30M88,43H84"


def write(name: str, text: str) -> None:
    """以固定换行生成资源，重复执行不改变未修改的文件。"""
    (DRAWABLE / name).write_text(text + "\n", encoding="utf-8")


def vector(body: str) -> str:
    """所有图标使用同一个 108 单位坐标系。"""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 原创「晴空轻云」标识，由 tools/generate_brand_assets.py 生成。 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
{body}
</vector>'''


BACKGROUND = '''    <path android:pathData="M0,0H108V108H0Z">
        <aapt:attr name="android:fillColor">
            <gradient android:startX="14" android:startY="0" android:endX="94" android:endY="108" android:type="linear">
                <item android:offset="0" android:color="#3D9DF2" />
                <item android:offset="1" android:color="#1452A6" />
            </gradient>
        </aapt:attr>
    </path>'''
FOREGROUND = f'''    <path android:pathData="{RAYS}" android:fillColor="#00000000"
        android:strokeColor="#FFE5A5" android:strokeWidth="2.8" android:strokeLineCap="round" />
    <path android:pathData="{SUN}">
        <aapt:attr name="android:fillColor">
            <gradient android:startX="58" android:startY="29" android:endX="72" android:endY="56" android:type="linear">
                <item android:offset="0" android:color="#FFE8AB" />
                <item android:offset="1" android:color="#FFB84D" />
            </gradient>
        </aapt:attr>
    </path>
    <group android:translateY="2">
        <path android:pathData="{CLOUD}" android:fillColor="#191B426D" />
    </group>
    <path android:pathData="{CLOUD}">
        <aapt:attr name="android:fillColor">
            <gradient android:startX="45" android:startY="35" android:endX="59" android:endY="73" android:type="linear">
                <item android:offset="0" android:color="#FFFFFF" />
                <item android:offset="1" android:color="#DEEEFF" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="{BREEZE}" android:fillColor="#00000000"
        android:strokeColor="#A8DCFF" android:strokeWidth="3" android:strokeLineCap="round" />'''

write("ic_launcher_background.xml", vector(BACKGROUND))
write("ic_launcher_foreground.xml", vector('    <group android:pivotX="54" android:pivotY="54" android:scaleX="0.88" android:scaleY="0.88">\n' + FOREGROUND + '\n    </group>'))
write("ic_launcher_monochrome.xml", vector(f'''    <group android:pivotX="54" android:pivotY="54" android:scaleX="0.88" android:scaleY="0.88">
    <path android:pathData="{SUN}" android:fillColor="#FFFFFF" />
    <path android:pathData="{CLOUD}" android:fillColor="#FFFFFF" />
    <path android:pathData="{RAYS} {BREEZE}" android:fillColor="#00000000"
        android:strokeColor="#FFFFFF" android:strokeWidth="3" android:strokeLineCap="round" />
    </group>'''))
write("ic_weather_logo.xml", vector('''    <path android:pathData="M26,4H82Q104,4 104,26V82Q104,104 82,104H26Q4,104 4,82V26Q4,4 26,4Z">
        <aapt:attr name="android:fillColor">
            <gradient android:startX="14" android:startY="4" android:endX="94" android:endY="104" android:type="linear">
                <item android:offset="0" android:color="#3D9DF2" />
                <item android:offset="1" android:color="#1452A6" />
            </gradient>
        </aapt:attr>
    </path>''' + "\n" + FOREGROUND))

# SVG 复用相同路径与渐变，不依赖原天气应用的素材。
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1080" viewBox="0 0 108 108" role="img" aria-labelledby="title desc">
  <title id="title">氢天气 · 晴空轻云</title>
  <desc id="desc">晴空蓝的圆角方形中，一朵轻云托住暖金色太阳，底部一笔微风。</desc>
  <defs>
    <linearGradient id="sky" x1="14" y1="4" x2="94" y2="104" gradientUnits="userSpaceOnUse"><stop stop-color="#3D9DF2"/><stop offset="1" stop-color="#1452A6"/></linearGradient>
    <linearGradient id="sun" x1="58" y1="29" x2="72" y2="56" gradientUnits="userSpaceOnUse"><stop stop-color="#FFE8AB"/><stop offset="1" stop-color="#FFB84D"/></linearGradient>
    <linearGradient id="cloud" x1="45" y1="35" x2="59" y2="73" gradientUnits="userSpaceOnUse"><stop stop-color="white"/><stop offset="1" stop-color="#DEEEFF"/></linearGradient>
  </defs>
  <rect x="4" y="4" width="100" height="100" rx="22" fill="url(#sky)"/>
  <path d="{RAYS}" fill="none" stroke="#FFE5A5" stroke-width="2.8" stroke-linecap="round"/>
  <path d="{SUN}" fill="url(#sun)"/>
  <path d="{CLOUD}" fill="#1B426D" opacity=".1" transform="translate(0 2)"/>
  <path d="{CLOUD}" fill="url(#cloud)"/>
  <path d="{BREEZE}" fill="none" stroke="#A8DCFF" stroke-width="3" stroke-linecap="round"/>
</svg>
'''
BRAND.mkdir(parents=True, exist_ok=True)
(BRAND / "hydrogen-weather-logo.svg").write_text(svg, encoding="utf-8")
