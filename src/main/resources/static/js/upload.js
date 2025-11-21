document.addEventListener("DOMContentLoaded",()=>{
    var buttonSelectFile = document.getElementById("selectFile")
    var selectFile = document.getElementById("file")
    var dataTypeSelector = document.getElementById("dataTypeSelector")
    var submitFile = document.getElementById("submit")
    var regions = document.getElementsByName("region")
    var gameIdSection = document.getElementById("gameIdSection")
    var gameIdInput = document.getElementById("gameIdInput")
    var pjskRegion = ""
    var filePath;

    // Show/hide game ID field based on file type selection
    dataTypeSelector.addEventListener("change", (event)=>{
        if(dataTypeSelector.value === "mysekai"){
            gameIdSection.style.display = "block";
        } else {
            gameIdSection.style.display = "none";
        }
    })

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
            formData.append("region", pjskRegion)
            
            // Determine endpoint based on file type
            var endpoint = "/api/v1/pjsk/upload/suite";
            if(dataTypeSelector.value === "mysekai"){
                endpoint = "/api/v1/pjsk/upload/mysekai";
                
                // Validate game ID is provided for mysekai
                var trimmedGameId = gameIdInput.value.trim();
                if(trimmedGameId === ""){
                    showCustomToast('error', '错误', '请提供游戏ID', 4000);
                    return;
                }
                
                // Add trimmed game ID to form data
                formData.append("gameId", trimmedGameId)
            }
            
            fetch(endpoint, {
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