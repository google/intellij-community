from pydantic import BaseModel, Field

class Model(BaseModel):
    a: str | None = Field("default")