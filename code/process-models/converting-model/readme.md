# Converting Models

Contains models shared by the [converting-process](../../processes/converting-process/) and
[loading-process](../../processes/loading-process/).

## Design

The two processes communicate through a file-based protocol. The converter serializes [instructions](src/main/java/nu/marginalia/converting/instruction/Instruction.java)
to file, which are deserialized by the loader and fed into an [instructions](src/main/java/nu/marginalia/converting/instruction/Interpreter.java). 

The instructions implement a visitor pattern.

Conceptually the pattern can be thought of a bit like remote function calls over file,
or a crude instructions-based programming language. 

This 

```java
producer.foo("cat");
producer.bar("milk", "eggs", "bread");
```

translates through this paradigm, to this:

```
(producer)
writeInstruction(DoFoo("Cat"))
writeInstruction(DoBar("Milk", "Eggs", "Bread"))

(consumer)
while read instruction:
  interpreter.apply(instruction)
  
(Interpreter)
doFoo(animal):
  ...
doBar(ingredients):
  ...

(doFoo)
DoFoo(animal):
  apply(interpreter):
    interpreter.foo(animal)

(doBar)
DoBar(ingredients):
  apply(interpreter):
    interpreter.bar(ingredients)
```
