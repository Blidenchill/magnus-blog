# 自托管字体说明

## AlimamaFangYuan-600.woff2（约 473 KB，3884 字形）

- **来源**：阿里妈妈方圆体（可变字体），经 npmmirror 包 `@fontpkg/alimama-fangyuanti-vf` 取得。
  官方授权：阿里妈妈系列字体面向全社会**永久免费商用**（含网页嵌入），见阿里妈妈官网字体声明。
- **加工**：`fontTools.varLib.instancer` 实例化到 wght=600（去掉变量数据），
  再用 `pyftsubset` 子集化：GB2312 一级字库（3755 字）+ ASCII + 常用标点 + 站点固定文案。
- **用途**：仅 `--font-display`（标题、站名、图注）。正文用系统圆体（Yuanti SC / 幼圆）兜底。
- **注意**：一级字库外的生僻字会回退到系统圆体显示，属预期行为。
  若需补充字符，重新生成字表并重复上述子集化流程即可。
