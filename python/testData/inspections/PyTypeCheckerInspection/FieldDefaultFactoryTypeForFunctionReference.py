# testFieldDefaultFactoryTypeForSimpleReference.py
from dataclasses import dataclass, field
from typing import Callable

def make_str() -> str:
    return "hello"

@dataclass
class A:
    x: int = <warning descr="Expected type 'int', got 'str' instead">field(default_factory=make_str)</warning>