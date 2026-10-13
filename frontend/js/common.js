// let commonURL = "http://192.168.50.115:8081";
let commonURL = "/api";
// 设置后台服务地址
axios.defaults.baseURL = commonURL;
axios.defaults.timeout = 2000;
// request拦截器，将用户token放入头中
let token = sessionStorage.getItem("token");
axios.interceptors.request.use(
  config => {
    if(token) config.headers['authorization'] = token
    return config
  },
  error => {
    console.log(error)
    return Promise.reject(error)
  }
)
axios.interceptors.response.use(function (response) {
  // 判断执行结果
  if (!response.data.success) {
    return Promise.reject(response.data.errorMsg)
  }
  return response.data;
}, function (error) {
  // 一般是服务端异常或者网络异常
  console.log(error)
  if(error.response.status == 401){
    // 未登录，跳转
    setTimeout(() => {
      location.href = "/login.html"
    }, 200);
    return Promise.reject("请先登录");
  }
  return Promise.reject("服务器异常");
});
axios.defaults.paramsSerializer = function(params) {
  let p = "";
  Object.keys(params).forEach(k => {
    if(params[k]){
      p = p + "&" + k + "=" + params[k]
    }
  })
  return p;
}
const util = {
  commonURL,
  getUrlParam(name) {
    let reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)", "i");
    let r = window.location.search.substr(1).match(reg);
    if (r != null) {
      return decodeURI(r[2]);
    }
    return "";
  },
  formatPrice(val) {
    if (typeof val === 'string') {
      if (isNaN(val)) {
        return null;
      }
      // 价格转为整数
      const index = val.lastIndexOf(".");
      let p = "";
      if (index < 0) {
        // 无小数
        p = val + "00";
      } else if (index === p.length - 2) {
        // 1位小数
        p = val.replace("\.", "") + "0";
      } else {
        // 2位小数
        p = val.replace("\.", "")
      }
      return parseInt(p);
    } else if (typeof val === 'number') {
      if (!val) {
        return null;
      }
      const s = val + '';
      if (s.length === 0) {
        return "0.00";
      }
      if (s.length === 1) {
        return "0.0" + val;
      }
      if (s.length === 2) {
        return "0." + val;
      }
      const i = s.indexOf(".");
      if (i < 0) {
        return s.substring(0, s.length - 2) + "." + s.substring(s.length - 2)
      }
      const num = s.substring(0, i) + s.substring(i + 1);
      if (i === 1) {
        // 1位整数
        return "0.0" + num;
      }
      if (i === 2) {
        return "0." + num;
      }
      if (i > 2) {
        return num.substring(0, i - 2) + "." + num.substring(i - 2)
      }
    }
  }
}

// ===== AI 智能客服（全站浮动入口，SSE 流式）=====
// 注意：前面 util 对象结尾无分号，这里必须用 ;( 开头，防止 ASI 合并解析报错
;(function () {
  // 1. 浮动按钮 + 对话面板
  let btn = document.createElement("div");
  btn.id = "aiChatBtn";
  btn.innerHTML = "AI 客服";
  btn.style.cssText = "position:fixed;right:20px;bottom:80px;z-index:9999;width:64px;height:64px;border-radius:50%;background:#ff5000;color:#fff;font-size:14px;display:flex;align-items:center;justify-content:center;cursor:pointer;box-shadow:0 2px 10px rgba(0,0,0,.3);user-select:none;";

  let panel = document.createElement("div");
  panel.id = "aiChatPanel";
  panel.style.cssText = "position:fixed;right:20px;bottom:150px;z-index:9999;width:360px;height:480px;background:#fff;border-radius:12px;box-shadow:0 4px 20px rgba(0,0,0,.25);display:none;flex-direction:column;overflow:hidden;";
  panel.innerHTML = `
    <div style="height:48px;background:#ff5000;color:#fff;line-height:48px;padding:0 16px;font-size:16px;display:flex;justify-content:space-between;">
      <span>AI 智能客服</span>
      <span id="aiChatClose" style="cursor:pointer;">×</span>
    </div>
    <div id="aiChatMsgs" style="flex:1;overflow-y:auto;padding:12px;background:#f5f5f5;"></div>
    <div style="height:56px;display:flex;border-top:1px solid #eee;">
      <input id="aiChatInput" type="text" placeholder="问点啥？比如：推荐一家火锅店" style="flex:1;border:none;outline:none;padding:0 12px;font-size:14px;" />
      <button id="aiChatSend" style="width:64px;border:none;background:#ff5000;color:#fff;font-size:14px;cursor:pointer;">发送</button>
    </div>`;
  document.body.appendChild(btn);
  document.body.appendChild(panel);

  let msgs = document.getElementById("aiChatMsgs");
  let input = document.getElementById("aiChatInput");
  let send = document.getElementById("aiChatSend");

  // 2. 展开/收起
  btn.onclick = () => { panel.style.display = panel.style.display === "none" ? "flex" : "none"; };
  document.getElementById("aiChatClose").onclick = () => { panel.style.display = "none"; };

  // 3. 消息气泡
  function addMsg(text, fromUser) {
    let div = document.createElement("div");
    div.style.cssText = "margin:6px 0;max-width:80%;padding:8px 12px;border-radius:8px;font-size:14px;line-height:1.5;word-break:break-all;" +
      (fromUser ? "margin-left:auto;background:#ff5000;color:#fff;" : "background:#fff;color:#333;");
    div.textContent = text;
    msgs.appendChild(div);
    msgs.scrollTop = msgs.scrollHeight;
    return div;
  }

  // 4. SSE 流式发送
  function sendMsg() {
    let question = input.value.trim();
    if (!question) return;
    addMsg(question, true);
    input.value = "";
    send.disabled = true;
    send.textContent = "…";
    let answer = addMsg("", false);

    // 店铺详情页自动带上 shopId（?id=xx），其余页面全站检索
    let shopId = "";
    let id = util.getUrlParam("id");
    if (id && location.pathname.indexOf("shop-detail") >= 0) {
      shopId = "&shopId=" + id;
    }
    // EventSource 不能带 header；/chat/** 后端已放行，无需 token
    let es = new EventSource(util.commonURL + "/chat/stream?question=" + encodeURIComponent(question) + shopId);
    es.onmessage = function (ev) {
      if (ev.data === "[DONE]") {
        es.close();
        send.disabled = false;
        send.textContent = "发送";
        return;
      }
      answer.textContent += ev.data; // 逐字追加 = 打字机效果
      msgs.scrollTop = msgs.scrollHeight;
    };
    es.onerror = function () {
      es.close();
      send.disabled = false;
      send.textContent = "发送";
    };
  }

  send.onclick = sendMsg;
  input.onkeydown = (e) => { if (e.key === "Enter") sendMsg(); };
})();
