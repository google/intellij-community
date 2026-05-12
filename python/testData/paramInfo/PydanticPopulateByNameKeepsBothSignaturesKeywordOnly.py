from pydantic import Field, BaseModel

class MyModel(BaseModel, populate_by_name=True):
    a: str = Field(alias='z')

MyModel(<arg1>)