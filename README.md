# Java Object Layout (JOL)

JOL (Java Object Layout) is the tiny toolbox to analyze object layout
in JVMs. These tools are using Unsafe, JVMTI, and Serviceability Agent (SA)
heavily to decode the actual object layout, footprint, and references.
This makes JOL much more accurate than other tools relying on heap dumps,
specification assumptions, etc.

## Usage

### JOL Samples

You can have a brief tour of JOL capabilities by looking through the [JOL Samples](https://github.com/openjdk/jol/tree/master/jol-samples/src/main/java/org/openjdk/jol/samples). You can run them from the IDE, or using the JAR file:

    $ java -cp jol-samples/target/jol-samples.jar org.openjdk.jol.samples.JOLSample_01_Basic

### Use as Library Dependency

[Maven Central](https://repo.maven.apache.org/maven2/org/openjdk/jol/jol-core/)
contains the latest releases. You can use them right away with this Maven dependency:

    <dependency>
        <groupId>org.openjdk.jol</groupId>
        <artifactId>jol-core</artifactId>
        <version>put-the-version-here</version>
    </dependency>

JOL module would try to self-attach as Java Agent, if possible. If you are using JOL as the library,
it is recommended to add `Premain-Class` and `Launcher-Agent` attributes to the
[final JAR manifest](https://github.com/openjdk/jol/blob/a549b7410045167238716677dac3de221951da2d/jol-samples/pom.xml#L132-L133).

### Use as Command Line Tool

Build produces the self-contained executable JAR in `jol-cli/target/jol-cli.jar`.
Published Maven artifacts also include the executable JAR that one can download
and start using right away. The JAR is published both at
`jol-cli-$version-full.jar` at [Maven Central](https://repo.maven.apache.org/maven2/org/openjdk/jol/jol-cli/) or [here](https://builds.shipilev.net/jol/).

List the supported commands with `-h`:

    $ java -jar jol-cli.jar -h
    Usage: jol-cli.jar <mode> [optional arguments]*

    Available operations:
                 externals: Show object externals: objects reachable from a given instance
                 footprint: Show the footprint of all objects reachable from a sample instance
            heapdump-boxes: Read a heap dump and look for data that looks duplicated, focusing on boxes
       heapdump-duplicates: Read a heap dump and look for data that looks duplicated
        heapdump-estimates: Read a heap dump and estimate footprint in different VM modes
            heapdump-stats: Read a heap dump and print simple statistics
      heapdump-stringdedup: Read a heap dump and look for Strings that can be deduplicated
                 internals: Show object internals: field layout, default contents, object header
       internals-estimates: Same as 'internals', but simulate class layout in different VM modes

A brief tour of commands follows.

#### "internals"

This dives into Object layout: field layout within the object, header information, field values, alignment/padding losses.

    $ java -jar jol-cli.jar internals java.util.HashMap
    # VM mode: 64 bits
    # Compressed references (oops): 3-bit shift
    # Compressed class pointers: 3-bit shift
    # Object alignment: 8 bytes
    #                       ref, bool, byte, char, shrt,  int,  flt,  lng,  dbl
    # Field sizes:            4,    1,    1,    2,    2,    4,    4,    8,    8
    # Array element sizes:    4,    1,    1,    2,    2,    4,    4,    8,    8
    # Array base offsets:    16,   16,   16,   16,   16,   16,   16,   16,   16

    Instantiated the sample instance via default constructor.

    java.util.HashMap object internals:
    OFF  SZ                       TYPE DESCRIPTION               VALUE
      0   8                            (object header: mark)     0x0000000000000001 (non-biasable; age: 0)
      8   4                            (object header: class)    0x000afde8
     12   4              java.util.Set AbstractMap.keySet        null
     16   4       java.util.Collection AbstractMap.values        null
     20   4                        int HashMap.size              0
     24   4                        int HashMap.modCount          0
     28   4                        int HashMap.threshold         0
     32   4                      float HashMap.loadFactor        0.75
     36   4   java.util.HashMap.Node[] HashMap.table             null
     40   4              java.util.Set HashMap.entrySet          null
     44   4                            (object alignment gap)    
    Instance size: 48 bytes
    Space losses: 0 bytes internal + 4 bytes external = 4 bytes total

#### "externals"

This dives into the Object graphs layout: list objects reachable from the instance,
their addresses, paths through the reachability graph, etc (is more
convenient with API though).

    $ java -jar jol-cli.jar externals java.lang.String
    # VM mode: 64 bits
    # Compressed references (oops): 3-bit shift
    # Compressed class pointers: 3-bit shift
    # Object alignment: 8 bytes
    #                       ref, bool, byte, char, shrt,  int,  flt,  lng,  dbl
    # Field sizes:            4,    1,    1,    2,    2,    4,    4,    8,    8
    # Array element sizes:    4,    1,    1,    2,    2,    4,    4,    8,    8
    # Array base offsets:    16,   16,   16,   16,   16,   16,   16,   16,   16

    Instantiated the sample instance via default constructor.

    java.lang.String@61f8bee4d object externals:
              ADDRESS       SIZE TYPE             PATH                           VALUE
            61fa01280         24 java.lang.String                                (object)
            61fa01298 8055156072 (something else) (somewhere else)               (something else)
            7ffc00000         16 byte[]           .value                         []

    Addresses are stable after 1 tries.

#### "footprint"

This gets the object footprint estimate, similar to the object externals, but tabulated.

    $ java -jar jol-cli.jar footprint java.security.SecureRandom
    # VM mode: 64 bits
    # Compressed references (oops): 3-bit shift
    # Compressed class pointers: 3-bit shift
    # Object alignment: 8 bytes
    #                       ref, bool, byte, char, shrt,  int,  flt,  lng,  dbl
    # Field sizes:            4,    1,    1,    2,    2,    4,    4,    8,    8
    # Array element sizes:    4,    1,    1,    2,    2,    4,    4,    8,    8
    # Array base offsets:    16,   16,   16,   16,   16,   16,   16,   16,   16

    Instantiated the sample instance via default constructor.

    java.security.SecureRandom@7fac631bd footprint:

    Table is sorted by "SUM".
    Printing first 30 lines. Use -DprintFirst=# to override.

               COUNT             AVG             SUM    DESCRIPTION
    ------------------------------------------------------------------------------------------------
                 488              46          22.504    byte[]
                 488              24          11.712    java.lang.String
                 318              32          10.176    java.util.concurrent.ConcurrentHashMap.Node
                  53              64           3.392    java.security.Provider.Service
                  41              80           3.280    java.util.HashMap.Node[]
                   2           1.552           3.104    java.util.concurrent.ConcurrentHashMap.Node[]
                 124              24           2.976    java.security.Provider.ServiceKey
                  66              32           2.112    java.util.HashMap.Node
                  40              48           1.920    java.util.HashMap
                  66              24           1.584    java.security.Provider.UString
                  26              28             752    java.lang.Object[]
                  40              16             640    java.util.HashMap.EntrySet
                  26              24             624    java.util.ArrayList
                  14              32             448    java.security.Provider.EngineDescription
                   5              72             360    java.lang.reflect.Field
                   4              72             288    java.lang.reflect.Constructor
                   2             128             256    java.lang.Class
                   5              40             200    java.util.LinkedHashMap.Entry
                   1             144             144    java.lang.ClassValue.Entry[]
                   2              64             128    java.lang.Class.ReflectionData
                   2              64             128    java.util.concurrent.ConcurrentHashMap
                   1             104             104    sun.security.provider.Sun
                   1              80              80    java.util.WeakHashMap.Entry[]
                   2              40              80    java.lang.ref.SoftReference
                   1              64              64    java.lang.ClassValue.ClassValueMap
                   2              32              64    java.lang.ClassValue.Entry
                   1              64              64    java.security.SecureRandom
                   2              28              56    java.lang.reflect.Field[]
                   1              56              56    java.lang.Module
                   1              56              56    java.util.LinkedHashMap
                 ...             ...             ...    ...
                  16             320             360    <other>
    ------------------------------------------------------------------------------------------------
               1.841           3.454          67.712    <total>

## Reporting Bugs

You may find unresolved bugs and feature request in 
[JDK Bug System](https://bugs.openjdk.java.net/issues/?jql=project%20%3D%20CODETOOLS%20AND%20resolution%20%3D%20Unresolved%20AND%20component%20%3D%20tools%20AND%20Subcomponent%20%3D%20jol) 
Please submit the new bug there:
 * Project: `CODETOOLS`
 * Component: `tools`
 * Sub-component: `jol`

If you don't have the access to JDK Bug System, submit the bug report at [Issues](https://github.com/openjdk/jol/issues) here, and wait for maintainers to pick that up.

## Development

JOL project accepts pull requests, like other OpenJDK projects.
If you have never contributed to OpenJDK before, then bots would require you to [sign OCA first](https://openjdk.java.net/contribute/).
Normally, you don't need to post patches anywhere else, or post to mailing lists, etc.
If you do want to have a wider discussion about JOL, please refer to [jol-dev](https://mail.openjdk.java.net/mailman/listinfo/jol-dev).

Compile and run tests:

    $ mvn clean verify

Tests would normally run in many JVM configurations. If you are contributing the code,
please try to run the build on multiple JDK releases, most importantly 8u and 11u.
GitHub workflow "JOL Pre-Integration Tests" should pass with your changes.

## Related projects

* [IntelliJ IDEA JOL Plugin](https://github.com/stokito/IdeaJol) can estimate object size and has an inspection to find heavy classes
