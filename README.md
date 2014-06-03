## Overview

[Jackson](/FasterXML/jackson) (Java) dataformat module that supports reading and writing 
[CBOR](https://www.rfc-editor.org/info/rfc7049)
("Concise Binary Object Representation") encoded data.
Module extends standard Jackson streaming API (`JsonFactory`, `JsonParser`, `JsonGenerator`), and as such works seamlessly with all the higher level data abstractions (data binding, tree model, and pluggable extensions).

[![Build Status](https://fasterxml.ci.cloudbees.com/job/jackson-dataformat-cbor-master/badge/icon)](https://fasterxml.ci.cloudbees.com/job/jackson-dataformat-cbor-master/)

## Status

As of version 2.4.0, this module is considered stable and production quality. Similar to JSON- and other JSON-like
backends, it implementsfull support for all levels (streaming, data-binding, tree model).

### Limitations

Minor limitations exist with respect to advanced type-handling of `CBOR` format:

* While tags are written for some types (`BigDecimal`, `BigInteger`), they are not handling on parsing

# Maven dependency

To use this extension on Maven-based projects, use following dependency:

```xml
<dependency>
  <groupId>com.fasterxml.jackson.dataformat</groupId>
  <artifactId>jackson-dataformat-cbor</artifactId>
  <version>2.4.0</version>
</dependency>
```

(or whatever version is most up-to-date at the moment)

## Usage

Basic usage is by using `CborFactory` in places where you would usually use `JsonFactory`:

```java
CBORFactory f = new CBORFactory();
ObjectMapper mapper = new ObjectMapper(f);
// and then read/write data as usual
SomeType value = ...;
byte[] cborData = mapper.writeValueAsBytes(value);
SomeType otherValue = mapper.readValue(cborData, SomeType.class);
```

Implementation allows use of any of 3 main operating modes:

* Streaming API (`CBORParser` and `CBORGenerator`)
* Databinding (via `ObjectMapper` / `ObjectReader` / `ObjectWriter`)
* Tree Model (using `TreeNode`, or its concrete subtype, `JsonNode` -- not JSON-specific despite the name)

and all the usual data-binding use cases exactly like when using `JSON` or `Smile` (2 canonical 100% supported Jackson data formats).
