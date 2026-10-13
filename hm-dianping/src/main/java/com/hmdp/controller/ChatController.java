package com.hmdp.controller;

import com.hmdp.service.impl.ChatServiceImpl;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatServiceImpl chatServiceImpl;

    /** AI 客服：SSE 流式对话接口 */
    @GetMapping(value = "/stream", produces ="text/event-stream;charset=UTF-8")
    public SseEmitter streamChat(
            @RequestParam("question") String question,
            @RequestParam(value = "shopId", required = false) Long shopId) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = 不超时
        chatServiceImpl.streamChat(question, shopId, emitter);
        return emitter;
    }
}
