from pydantic import BaseModel, Field

class MyModel(BaseModel):
    a: str | None = Field("default")
    b: str | None = Field()

MyModel(<arg1>)