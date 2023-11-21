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

#### "heapdump-estimates"

    $ java
    Heap Dump: sample-clion.hprof.gz

    'Overhead' comes from additional metadata, representation and alignment losses.
    'JVM mode' is the relative footprint change compared to the best JVM mode in this JDK.
    'Upgrade From' is the relative footprint change against the same mode in other JDKs.

    Read progress: 269M... 538M... 808M... 1077M... 1346M... 1616M... DONE

    === Overall Statistics

       17426K,     Total objects
         682M,     Total data size
        39,15,     Average data per object

    === Stock 32-bit OpenJDK

    Footprint,   Overhead,     Description
         897M,     +31,6%,     32-bit (<4 GB heap)

    === Stock 64-bit OpenJDK (JDK < 15)

    Footprint,   Overhead,   JVM Mode,     Description
        1526M,    +123,8%,     +61,9%,     64-bit, no comp refs (>32 GB heap, default align)
         942M,     +38,2%,         0%,     64-bit, comp refs (<32 GB heap, default align)
        1026M,     +50,5%,      +8,9%,     64-bit, comp refs with large align (   32..64GB heap,  16-byte align)
        1133M,     +66,2%,     +20,2%,     64-bit, comp refs with large align (  64..128GB heap,  32-byte align)
        1499M,    +119,8%,     +59,0%,     64-bit, comp refs with large align ( 128..256GB heap,  64-byte align)
        2556M,    +274,7%,    +171,1%,     64-bit, comp refs with large align ( 256..512GB heap, 128-byte align)
        4768M,    +599,1%,    +405,7%,     64-bit, comp refs with large align (512..1024GB heap, 256-byte align)

    === Stock 64-bit OpenJDK (JDK >= 15)

                                         Upgrade From:
    Footprint,   Overhead,   JVM Mode,   JDK < 15,     Description
        1423M,    +108,6%,     +51,0%,      -6,8%,     64-bit, no comp refs, but comp classes (>32 GB heap, default align)
         942M,     +38,2%,         0%,        ~0%,     64-bit, comp refs (<32 GB heap, default align)
        1026M,     +50,4%,      +8,9%,        ~0%,     64-bit, comp refs with large align (   32..64GB heap,  16-byte align)
        1132M,     +66,0%,     +20,1%,      -0,1%,     64-bit, comp refs with large align (  64..128GB heap,  32-byte align)
        1498M,    +119,6%,     +59,0%,        ~0%,     64-bit, comp refs with large align ( 128..256GB heap,  64-byte align)
        2556M,    +274,7%,    +171,2%,        ~0%,     64-bit, comp refs with large align ( 256..512GB heap, 128-byte align)
        4768M,    +599,1%,    +406,0%,         0%,     64-bit, comp refs with large align (512..1024GB heap, 256-byte align)

    === Experimental 64-bit OpenJDK: Lilliput, 64-bit headers

                                         Upgrade From:
    Footprint,   Overhead,   JVM Mode,   JDK < 15,  JDK >= 15,     Description
        1373M,    +101,3%,     +51,9%,     -10,0%,      -3,5%,     64-bit, no comp refs, but comp classes (>32 GB heap, default align)
         904M,     +32,6%,         0%,      -4,1%,      -4,0%,     64-bit, comp refs (<32 GB heap, default align)
        1001M,     +46,8%,     +10,7%,      -2,5%,      -2,4%,     64-bit, comp refs with large align (   32..64GB heap,  16-byte align)
        1116M,     +63,6%,     +23,4%,      -1,5%,      -1,4%,     64-bit, comp refs with large align (  64..128GB heap,  32-byte align)
        1496M,    +119,3%,     +65,4%,      -0,2%,      -0,1%,     64-bit, comp refs with large align ( 128..256GB heap,  64-byte align)
        2556M,    +274,7%,    +182,6%,        ~0%,        ~0%,     64-bit, comp refs with large align ( 256..512GB heap, 128-byte align)
        4768M,    +599,1%,    +427,3%,         0%,         0%,     64-bit, comp refs with large align (512..1024GB heap, 256-byte align)

    === Experimental 64-bit OpenJDK: Lilliput, 32-bit headers

                                         Upgrade From:
    Footprint,   Overhead,   JVM Mode,   JDK < 15,  JDK >= 15,    Lill-64,      Description
        1283M,     +88,2%,     +59,8%,     -15,9%,      -9,8%,      -6,5%,      64-bit, no comp refs, but comp classes (>32 GB heap, default align)
         803M,     +17,7%,         0%,     -14,8%,     -14,8%,     -11,2%,      64-bit, comp refs (<32 GB heap, default align)
         858M,     +25,9%,      +6,9%,     -16,4%,     -16,3%,     -14,2%,      64-bit, comp refs with large align (   32..64GB heap,  16-byte align)
         972M,     +42,5%,     +21,0%,     -14,2%,     -14,1%,     -12,9%,      64-bit, comp refs with large align (  64..128GB heap,  32-byte align)
        1477M,    +116,5%,     +83,9%,      -1,5%,      -1,4%,      -1,3%,      64-bit, comp refs with large align ( 128..256GB heap,  64-byte align)
        2554M,    +274,5%,    +218,1%,     -46,4%,        ~0%,        ~0%,      64-bit, comp refs with large align ( 256..512GB heap, 128-byte align)
        4768M,    +599,0%,    +493,8%,        ~0%,        ~0%,        ~0%,      64-bit, comp refs with large align (512..1024GB heap, 256-byte align)

#### "heapdump-stats"



#### "heapdump-duplicates"

Reads the heap dump and tries to identify the objects that have the same contents. These objects might be de-duplicated,
if possible. It would print both the summary report, and more verbose report per class. 

    $ java -jar jol-cli.jar heapdump-duplicates sample-clion.hprof.gz
    Heap Dump: sample-clion.hprof.gz
    Read progress: 269M... 538M... 808M... 1077M... 1346M... 1616M... DONE

    Hotspot Layout Simulation (JDK 17, Current VM: 12-byte object headers, 4-byte references, 8-byte aligned objects, 8-byte aligned array bases)

    === Potential Duplication Candidates

    Table is sorted by "SUM SIZE".
    Printing first 30 lines. Use -DprintFirst=# to override.

                DUPS        SUM SIZE    CLASS
    ------------------------------------------------------------------------------------------------
             449.993     124.302.944    Object[]
             656.318      31.629.792    byte[]
             661.645      26.465.800    com.jetbrains.cidr.lang.symbols.cpp.OCMacroSymbol
             610.393      19.532.576    java.util.HashMap$Node
             177.670       6.566.216    int[]
             273.211       6.557.064    java.util.ArrayList
              93.361       2.987.552    java.util.concurrent.ConcurrentHashMap$Node
              59.492       1.903.744    com.jetbrains.cidr.lang.types.OCReferenceTypeSimple
              39.348       1.573.920    com.jetbrains.cidr.lang.types.OCPointerType
              62.699       1.504.776    java.lang.String
              32.250       1.290.000    org.languagetool.rules.patterns.PatternToken
              50.661       1.215.864    com.intellij.openapi.util.Pair
               7.536       1.033.872    long[]
              25.407       1.016.280    com.jetbrains.cidr.lang.types.OCIntType
              63.268       1.012.288    java.util.concurrent.atomic.AtomicReference
              29.521         944.672    com.intellij.openapi.vfs.newvfs.impl.VirtualFileImpl
              56.675         906.800    java.lang.Integer
              21.845         699.040    com.jetbrains.cidr.lang.symbols.OCSymbolReference$GlobalReference
              42.183         674.928    java.lang.Object
              27.481         659.544    com.intellij.util.keyFMap.OneElementFMap
              19.553         625.696    com.jetbrains.cidr.lang.symbols.ComplexTextRange
              14.071         562.840    com.intellij.reference.SoftReference
               7.288         524.736    java.lang.reflect.Field
              21.370         512.880    com.jetbrains.cidr.lang.symbols.OCQualifiedName
              12.625         505.000    java.lang.ref.SoftReference
              10.224         490.752    java.util.HashMap
               2.362         481.664    boolean[]
               9.355         449.040    com.jetbrains.cidr.lang.preprocessor.OCMacroForeignLeafType
              17.707         424.968    com.jetbrains.cidr.lang.symbols.cpp.OCIncludeSymbol$IncludePath
              10.533         421.320    com.jetbrains.cidr.lang.preprocessor.OCMacroReferenceTokenType
                 ...             ...    ...
             307.252       9.288.760    <other>
    ------------------------------------------------------------------------------------------------
           3.873.297     246.765.328    <total>

    ...

    === com.jetbrains.cidr.lang.symbols.cpp.OCMacroSymbol Potential Duplicates
      DUPS: Number of instances with same data
      SIZE: Total size taken by duplicate instances

    Table is sorted by "SIZE".
    Printing first 30 lines. Use -DprintFirst=# to override.

                DUPS            SIZE    VALUE
    ------------------------------------------------------------------------------------------------
               1.044          41.760    (hash: b3d7653a1b45cdc7)
               1.044          41.760    (hash: dba02bbacfe63eb7)
               1.044          41.760    (hash: 31921ef6e494ca97)
               1.044          41.760    (hash: c2b4fb34818eb9ed)
               1.044          41.760    (hash: 31f79d3ace1161ca)
               1.044          41.760    (hash: 13f841d0438614c5)
               1.044          41.760    (hash: d45cdf077af876ad)
               1.044          41.760    (hash: 1b27a7c37cafc70e)
    ...

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
