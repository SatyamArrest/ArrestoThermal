package app.com.azusol.arrestothermal.data.models

data class ThermalDataModel(
    var tempData: ArrayList<ConstantModel> = ArrayList(),
    var thermal_imagepath: String="",
    var actual_imagepath: String="",
    var marked_imagepath: String="",
    var scale_imagepath: String="",
    var emissivity: String="",
    var humidity: String="",
    var air_temp: String="",
    var cameraModel: String=""
)
//{
////    constructor() : this("","","")
//}