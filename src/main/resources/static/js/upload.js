
document.addEventListener("DOMContentLoaded",()=>{
    var buttonSelectFile = document.getElementById("selectFile")
    var selectFile = document.getElementById("file")
    var dataTypeSelector = document.getElementById("dataTypeSelector")
    var submitFile = document.getElementById("submit")
    var regions = document.getElementsByName("region")
    var pjskRegion = ""
    var filePath;

    buttonSelectFile.addEventListener("click",(event)=>{
        selectFile.click()
    })

    selectFile.addEventListener("change",(event)=>{
        if(selectFile.files[0] !== undefined){
            buttonSelectFile.innerText = "已选择：" + selectFile.files[0].name
        }
    })

    submitFile.addEventListener("click",(event)=>{
        filePath = selectFile.files[0]
        regions.forEach(region=>{
            if(region.checked){
                pjskRegion = region.value
            }
        })
        if(filePath === undefined){
            showCustomToast('error', '错误', '请选择一个数据文件', 4000);
            return;
        }
        uploadFile(filePath)
    })
    function uploadFile(filePath){
        try {
            var formData = new FormData();
            formData.append("file", filePath)
            formData.append("filetype", dataTypeSelector.value)
            formData.append("region", pjskRegion)
            fetch("/api/upload", {
                    method: "POST",
                    body: formData,
            }).then((response) => {
                    response.json().then(data => {
                        console.log(data)
                        if(data.code / 100 === 200){
                            //var date = new Date(data.code)
                            showCustomToast('success', '上传成功', '资料上传成功！快去群里试试吧', 4000);
                            //showCustomToast('info', '数据日期', '数据日期为'+date, 4000);
                        }else {
                            showCustomToast('error', '出现错误', '出现错误，请联系管理员.' + response.message + "\mError code:"+response.code, 4000);
                        }
                    })
            })
        }catch(err){
            showCustomToast('error', '出现错误', '出现错误，请联系管理员.' + err, 4000);
        }

    }
})
window.addEventListener("load", (event)=>{
    setTimeout(()=>{
        showCustomToast('info', '欢迎使用', '上传你的啤酒烧烤数据吧', 4000);
    },500)
})

