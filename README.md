
### 使用方式

生成jar然后通过Java命令生成或者读取渠道包。

`ChannelRead`可以用于Android工程依赖。

读取渠道：

`java -jar xx.jar -r [apkpath]`


写入渠道：

`java -jar xx.jar -w [apkpath] -c [channel list txt] -o [result dir] `


详细的请参考工程源码。

### 编译工具

IDEA

### 参考文献

[apksig源码](https://android.googlesource.com/platform/tools/apksig/)

[v2 signature 官方解释](https://source.android.com/security/apksigning/v2)

