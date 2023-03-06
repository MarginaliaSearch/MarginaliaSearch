# Guarded Regex

This is a simple library for creating guarded regular expressions. Pattern matching in Java
is pretty slow even with compiled regular expressions.

Guarding them with a `startsWith()`, `endsWith()` or `contains()` can be an order of magnitude 
faster, but leads to an unfortunate spreading out of the logic across the pattern and the guard 
condition.

Guarded regexes aims to fix this. Instead of code like

```java 
Pattern pattern = Pattern.compile("[123]?foo(bar|baz){2,5}");

void ifTheThingDoTheThing(String str) {
  if (str.contains("foo") && pattern.matcher(str).matches()) {
    doTheThing();
  }
}
```

you get the more expressive variant

```java
GuardedRegex thingPredicate = 
    GuardedRegexFactory.contains("foo", "[123]?foo(bar|baz){2,5}");

void ifTheThingDoTheThing(String str) {
    if (thingPredicate.test(str)) {
      doTheThing();
    }
}
```

## Central Classes

* [GuardedRegexFactory](src/main/java/nu/marginalia/gregex/GuardedRegexFactory.java)