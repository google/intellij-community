from dataclasses import dataclass, field
from lib import do

@dataclass
class NewDataclass:
    val: str | None = field(default_factory=do)
