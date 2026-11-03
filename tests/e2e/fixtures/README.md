# E2E 图片夹具

放置测试用的小尺寸 JPEG/PNG 图片(例如 `sample.jpg`,≤ 1 MB)。

**只使用专门为测试生成的图片**,不使用真实私人照片(见 docs/technical-design.md §10.1)。
可以用以下命令生成一张纯色测试图:

```bash
# macOS(需要 ImageMagick 或 sips)
sips -z 600 800 -s format jpeg /System/Library/CoreServices/DefaultDesktop.jpg --out sample.jpg 2>/dev/null || true
```
