# How to Run

## Running a local node 

Use java command to start FullNode.jar or use the docker image [Stabila Deployment Scripts](https://github.com/stabilaprotocol/stabila-deployment).

### Running a Super Representative Node for mainnet

**Use the executable JAR(Recommended way):**

```bash
java -jar FullNode.jar -p your private key --executive -c your config.conf
Example:
java -jar FullNode.jar -p 650950B193DDDDB35B6E48912DD28F7AB0E7140C1BFDEFD493348F02295BD812 --executive -c config.conf

```

The IPs in the `config.conf` are officially declared by STABILA.

<details>
<summary>Correct output</summary>

```bash

20:43:18.138 INFO  [main] [o.t.p.FullNode](FullNode.java:21) Full node running.
20:43:18.486 INFO  [main] [o.t.c.c.a.Args](Args.java:429) Bind address wasn't set, Punching to identify it...
20:43:18.493 INFO  [main] [o.t.c.c.a.Args](Args.java:433) UDP local bound to: 10.0.8.146
20:43:18.495 INFO  [main] [o.t.c.c.a.Args](Args.java:448) External IP wasn't set, using checkip.amazonaws.com to identify it...
20:43:19.450 INFO  [main] [o.t.c.c.a.Args](Args.java:461) External address identified: 47.74.147.87
20:43:19.599 INFO  [main] [o.s.c.a.AnnotationConfigApplicationContext](AbstractApplicationContext.java:573) Refreshing org.springframework.context.annotation.AnnotationConfigApplicationContext@124c278f: startup date [Fri Apr 27 20:43:19 CST 2018]; root of context hierarchy
20:43:19.972 INFO  [main] [o.s.b.f.a.AutowiredAnnotationBeanPostProcessor](AutowiredAnnotationBeanPostProcessor.java:153) JSR-330 'javax.inject.Inject' annotation found and supported for autowiring
20:43:20.380 INFO  [main] [o.t.c.d.DynamicPropertiesStore](DynamicPropertiesStore.java:244) update latest block header timestamp = 0
20:43:20.383 INFO  [main] [o.t.c.d.DynamicPropertiesStore](DynamicPropertiesStore.java:252) update latest block header number = 0
20:43:20.393 INFO  [main] [o.t.c.d.DynamicPropertiesStore](DynamicPropertiesStore.java:260) update latest block header id = 00
20:43:20.394 INFO  [main] [o.t.c.d.DynamicPropertiesStore](DynamicPropertiesStore.java:265) update state flag = 0
20:43:20.559 INFO  [main] [o.t.c.c.TransactionCapsule](TransactionCapsule.java:83) Transaction create succeeded！
20:43:20.567 INFO  [main] [o.t.c.c.TransactionCapsule](TransactionCapsule.java:83) Transaction create succeeded！
20:43:20.568 INFO  [main] [o.t.c.c.TransactionCapsule](TransactionCapsule.java:83) Transaction create succeeded！
20:43:20.568 INFO  [main] [o.t.c.c.TransactionCapsule](TransactionCapsule.java:83) Transaction create succeeded！
20:43:20.569 INFO  [main] [o.t.c.c.TransactionCapsule](TransactionCapsule.java:83) Transaction create succeeded！
20:43:20.596 INFO  [main] [o.t.c.d.Manager](Manager.java:300) create genesis block
20:43:20.607 INFO  [main] [o.t.c.d.Manager](Manager.java:306) save block: BlockCapsule

```

Then observe whether block synchronization success，If synchronization successfully explains the success of the super node

</details>

## Advanced Configurations

Read the [Advanced Configurations](common/src/main/java/org/stabila/core/config/README.md).
