from datetime import datetime
from pydantic import BaseModel
from typing import List, Optional

class MedicalHistory(BaseModel):
    chronic_conditions: List[str]
    surgeries: List[str]
    fall_history: List[str]
    medication_history: List[str]

class LocationCoords(BaseModel):
    latitude: float
    longitude: float

class AccelerometerData(BaseModel):
    x: float
    y: float
    z: float

class PatientInfo(BaseModel):
    patient_id: str
    name: str
    age: int
    gender: str
    medical_history: MedicalHistory
    location_coords: Optional[LocationCoords]

class HeartRateData(BaseModel):
    patient_id: str
    heart_rate: float
    time_of_data: datetime
    accelerometer_data: Optional[AccelerometerData]
    air_pressure: Optional[float]
    temperature: Optional[float]
    step_count: Optional[int]

    class Config:
        schema_extra = {
            "example": {
                "patient_id": "P123456789",
                "heart_rate": 72.5,
                "time_of_data": "2024-07-17T14:35:00Z",
                "accelerometer_data": {
                    "x": 0.01,
                    "y": -0.02,
                    "z": 0.98
                },
                "air_pressure": 1012.3,
                "temperature": 22.5,
                "step_count": 5280
            }
        }
