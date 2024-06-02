本项目是提供一组在虚拟线程里面很有用但是被jdk屏蔽掉的API
全部都以同名的函数/类型的形式提供
包含以下功能
- CarrierThreadLocal
- 自定义调度器
- Continuation API 包含yield和run原语
- TerminatingThreadLocal API
- 特权LookUp
- java.lang.LiveStackFrameInfo获取当前栈信息，比如说Monitor信息，局部变量表及获取对应引用

原理很简单
代码如下
```java
    private static MethodHandles.Lookup fetchUnsafeHandler() {
        Class<MethodHandles.Lookup> lookupClass = MethodHandles.Lookup.class;

        try {
            Field implLookupField = lookupClass.getDeclaredField("IMPL_LOOKUP");
            long offset = UNSAFE.staticFieldOffset(implLookupField);
            return (MethodHandles.Lookup) UNSAFE.getObject(UNSAFE.staticFieldBase(implLookupField), offset);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
```
IMPL_LOOKUP这个是无视各种权限的lookup，从它这里获取到的MethodHandle无视任何权限
然后就可以好好玩拿到MethodHandle了

为了应对未来的Unsafe中的某些内存操作被移除的问题，在[寒老板](https://github.com/IceSoulHanxi)的一次群聊中提到了几个java动态库导出的符号，我们逆向了这些符号以及正向参考了openjdk源码，[使用Panama API封装了大部分的JNI操作](https://github.com/dreamlike-ocean/backend_qingyou/blob/main/dreamlike%E7%9A%84%E7%A7%81%E8%B4%A7/afterUnsafe.md),使得可以绕开模块化之类的限制让我们继续可以hack标准库，继续能拿到IMPL_LOOKUP字段

> [!WARNING]
> 目前只在Linux以及Windows x86_64,Mac(aarch64以及amd64)上测试过，其他平台可能需要评估兼容性问题


maven 坐标为，本库以multi-release的形式发布，所以你可以在jdk8-jdk22上使用，从jdk22开始默认使用的Panama的实现
```xml
<dependency>
  <groupId>io.github.dreamlike-ocean</groupId>
  <artifactId>unsafe-core</artifactId>
  <version>1.2</version>
</dependency>
```

当然你也会发现项目里面有另外一种写法，即获取到MethodHandle之后转换为`java.util.function.Function`，这两种形式核心原理是一样的
```java
    private static Function<Executor, Thread.Builder.OfVirtual> fetchVirtualThreadBuilder() {
        var tmp = Thread.ofVirtual().getClass();
        try {
            MethodHandle builderMethodHandle = IMPL_LOOKUP
                    .in(tmp)
                    .findConstructor(tmp, MethodType.methodType(void.class, Executor.class));
            MethodHandle lambdaFactory = LambdaMetafactory.metafactory(
                    IMPL_LOOKUP,
                    "apply",
                    MethodType.methodType(Function.class),
                    MethodType.methodType(Object.class, Object.class),
                    builderMethodHandle,
                    builderMethodHandle.type()
            ).getTarget();
            return (Function<Executor, Thread.Builder.OfVirtual>) lambdaFactory.invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

```
类似于lambda和sam


TerminatingThreadLocal API的则是比较复杂
这里用了内部的`jdk.internal.access.JavaLangAccess`的这个玩意强制打开限制，让我可以以继承的方式自定义它的销毁函数
这里使用bytebuddy继承然后转发到field上面 具体看代码吧。。。

对于给Vert.x的扩展，用的是Async/Await这种风格的，避免某些半瓶水再来烦我，我讲一下为什么要这样写
首先我写的await函数只是强制要求当前在Continuation中而已，没有传染性的
其次写`AsyncScope`只是帮你开启Continuation罢了

