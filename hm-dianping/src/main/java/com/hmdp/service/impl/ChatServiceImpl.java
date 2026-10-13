package com.hmdp.service.impl;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl {

    @Value("${ark.api-key}")
    private String apiKey;

    @Value("${ark.chat-model}")
    private String chatModel;

    @Resource
    private IShopService shopService;

    private static final ExecutorService CHAT_EXECUTOR = Executors.newSingleThreadExecutor();

    /** 流式对话：关键词检索相关店铺 → 拼上下文 → 豆包 SSE 逐字回复 */
    public void streamChat(String question, Long shopId, SseEmitter emitter) {
        CHAT_EXECUTOR.submit(() -> {
            HttpURLConnection conn = null;
            try {
                // ===== RAG 检索阶段 =====
                // 1. 关键词检索与问题最相关的 Top3 店铺
                List<Shop> topShops = rankShops(question, 3);
                // 2. 指定了 shopId 且不在结果里，强制补进去（用户正在看这家店）
                boolean contains = topShops.stream().anyMatch(s -> s.getId().equals(shopId));
                if (shopId != null && !contains) {
                    Shop shop = shopService.getById(shopId);
                    if (shop != null) {
                        topShops.add(0, shop);
                        if (topShops.size() > 3) {
                            topShops.remove(topShops.size() - 1);
                        }
                    }
                }

                // 3. 拼 system prompt（只塞检索到的店铺信息，控制 token）
                StringBuilder systemPrompt = new StringBuilder("你是烟火探店平台的智能客服，根据【店铺资料】回答用户问题；资料里没有的信息，如实说明不知道。");
                for (int i = 0; i < topShops.size(); i++) {
                    Shop s = topShops.get(i);
                    systemPrompt.append("\n【店铺").append(i + 1).append("】")
                            .append("名称：").append(s.getName())
                            .append("；商圈：").append(s.getArea())
                            .append("；地址：").append(s.getAddress())
                            .append("；评分：").append(s.getScore() == null ? 0 : s.getScore() / 10.0).append("分")
                            .append("；均价：").append(s.getAvgPrice()).append("元")
                            .append("；销量：").append(s.getSold())
                            .append("；评论数：").append(s.getComments())
                            .append("；营业时间：").append(s.getOpenHours());
                }

                // 4. 组装请求体（stream=true 开启流式）
                JSONObject body = new JSONObject();
                body.set("model", chatModel);
                body.set("stream", true);
                body.set("max_tokens", 512);
                JSONArray messages = new JSONArray();
                JSONObject sys = new JSONObject();
                sys.set("role", "system");
                sys.set("content", systemPrompt.toString());
                messages.add(sys);
                JSONObject user = new JSONObject();
                user.set("role", "user");
                user.set("content", question);
                messages.add(user);
                body.set("messages", messages);

                // 5. 发起 HTTP 请求
                URL url = new URL("https://ark.cn-beijing.volces.com/api/v3/chat/completions");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                // 6. 逐行读 SSE 流，逐字转发给前端
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JSONObject delta = JSONUtil.parseObj(data)
                            .getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("delta");
                    String content = delta.getStr("content");
                    if (content != null && !content.isEmpty()) {
                        emitter.send(SseEmitter.event().data(content));
                    }
                }
                emitter.send("[DONE]");   // 结束标记：前端收到后主动关闭连接，防止 EventSource 自动重连
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    // ===== RAG 检索：n-gram 关键词打分（零依赖，小数据量够用）=====
    private List<Shop> rankShops(String question, int limit) {
        // 1. 加载全部店铺（几十条，全量扫没问题）
        List<Shop> shops = shopService.list();
        // 2. 把问题切成 2-gram（近似分词，不引分词库）
        List<String> grams = toNgrams(question, 2);
        if (grams.isEmpty()) {
            return shops.size() > limit ? shops.subList(0, limit) : shops;
        }
        // 3. 给每个店铺打分：名称命中权重 3，其余字段命中权重 1
        List<Map.Entry<Shop, Double>> scored = new ArrayList<>();
        for (Shop shop : shops) {
            String nameDoc = StrUtil.nullToEmpty(shop.getName());
            String bodyDoc = StrUtil.nullToEmpty(shop.getArea()) + " " + StrUtil.nullToEmpty(shop.getAddress())
                    + " " + StrUtil.nullToEmpty(shop.getOpenHours());
            double score = 0;
            for (String g : grams) {
                if (nameDoc.contains(g)) {
                    score += 3;
                }
                if (bodyDoc.contains(g)) {
                    score += 1;
                }
            }
            scored.add(new AbstractMap.SimpleEntry<>(shop, score));
        }
        // 4. 按分数倒序取 Top
        return scored.stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /** 把文本切成连续 n-gram（只保留中文/字母/数字） */
    private List<String> toNgrams(String text, int n) {
        List<String> grams = new ArrayList<>();
        if (StrUtil.isBlank(text)) {
            return grams;
        }
        StringBuilder seg = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                seg.append(c);
            } else {
                addGrams(grams, seg.toString(), n);
                seg.setLength(0);
            }
        }
        addGrams(grams, seg.toString(), n);
        return grams;
    }

    private void addGrams(List<String> grams, String s, int n) {
        if (s.isEmpty()) {
            return;
        }
        if (s.length() < n) {
            grams.add(s);
            return;
        }
        for (int i = 0; i <= s.length() - n; i++) {
            grams.add(s.substring(i, i + n));
        }
    }
}
