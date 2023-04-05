本项目是提供一组在虚拟线程里面很有用但是被jdk屏蔽掉的API
全部都以同名的函数/类型的形式提供
包含以下功能
- CarrierThreadLocal
- 自定义调度器
- Continuation API 包含yield和run原语

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