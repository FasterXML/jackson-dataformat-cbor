## Overview

Jackson dataformat module that supports reading and writing 
[CBOR](https://www.rfc-editor.org/info/rfc7049)
("Concise Binary Object Representation") encoded data.
Module extends standard Jackson streaming API (`JsonFactory`, `JsonParser`, `JsonGenerator`), and as such works seamlessly with all the higher level data abstractions (data binding, tree model, and pluggable extensions).

[![Build Status](https://fasterxml.ci.cloudbees.com/job/jackson-dataformat-cbor-master/badge/icon)](https://fasterxml.ci.cloudbees.com/job/jackson-dataformat-cbor-master/)

## Status

Module is pre-alpha quality, fully experimental and not ready for general use.

# Maven dependency

To use this extension on Maven-based projects, use following dependency:

```xml
<dependency>
  <groupId>com.fasterxml.jackson.dataformat</groupId>
  <artifactId>jackson-dataformat-cbor</artifactId>
  <version>2.3.0</version>
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
