from pydantic import BaseModel, Field

class Model(BaseModel, populate_by_name=True):
  my_field: str = Field(alias="my_field")

m = Model(<arg1>)