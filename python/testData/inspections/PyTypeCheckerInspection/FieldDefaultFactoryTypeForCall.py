from dataclasses import dataclass, field
from typing import Callable

def make_factory() -> Callable[[], str]:
    def inner() -> str:
        return "hello"
    return inner

@dataclass
class B:
    y: int = <warning descr="Expected type 'int', got 'str' instead">field(default_factory=make_factory())</warning>