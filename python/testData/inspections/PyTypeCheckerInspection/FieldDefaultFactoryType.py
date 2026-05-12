from dataclasses import dataclass, field

@dataclass
class E:
    a: int = <warning descr="Expected type 'int', got 'str' instead">field(default_factory=(lambda: ""))</warning>