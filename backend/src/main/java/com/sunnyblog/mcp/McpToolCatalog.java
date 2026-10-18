package com.sunnyblog.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * JSON Schema catalog for the MCP tools exposed to AI clients.
 */
public final class McpToolCatalog {

    private McpToolCatalog() {
    }

    public static ArrayNode tools(ObjectMapper mapper) {
        ArrayNode tools = mapper.createArrayNode();

        ObjectNode list = tool(mapper, "list_articles",
                "列出博客文章。可按草稿/已发布状态和关键词筛选，返回文章 id、slug、标题、分类、标签和更新时间。修改文章前先用它找到文章。");
        ObjectNode listProps = properties(list);
        enumProperty(listProps, "status", "文章状态；all 表示全部", "all", "all", "draft", "published");
        stringProperty(listProps, "keyword", "标题或摘要关键词");
        numberProperty(listProps, "page", "页码，从 1 开始", 1, 1, null);
        numberProperty(listProps, "size", "每页数量，1-50", 10, 1, 50);
        list.put("inputSchema", schema(mapper, listProps, false));
        tools.add(list);

        ObjectNode get = tool(mapper, "get_article",
                "读取一篇文章的完整内容，包含 Markdown 正文、分类、标签、封面和状态。支持用 id 或 slug 查询。");
        ObjectNode getProps = properties(get);
        numberProperty(getProps, "id", "文章 id", null, 1, null);
        stringProperty(getProps, "slug", "文章 slug");
        get.put("inputSchema", schemaWithIdOrSlug(mapper, getProps));
        tools.add(get);

        ObjectNode taxonomy = tool(mapper, "list_taxonomy",
                "列出博客现有的分类和标签。创建或修改文章前建议先查看，避免使用不存在的分类。");
        taxonomy.put("inputSchema", schema(mapper, mapper.createObjectNode(), false));
        tools.add(taxonomy);

        ObjectNode create = tool(mapper, "create_article",
                "创建一篇博客文章。正文使用 Markdown；默认保存为草稿，status=published 会直接发布。分类和标签使用名称或 slug，不存在的标签会自动创建。");
        ObjectNode createProps = properties(create);
        stringProperty(createProps, "title", "文章标题，最多 120 字");
        stringProperty(createProps, "content", "Markdown 正文");
        stringProperty(createProps, "summary", "摘要，不填则自动从正文提取");
        stringProperty(createProps, "slug", "文章 URL slug，不填则根据标题生成");
        stringProperty(createProps, "category", "分类名称或 slug；不填时使用「笔记」或第一个分类");
        arrayProperty(createProps, "tags", "标签名称数组，不存在的标签会自动创建");
        stringProperty(createProps, "coverImage", "封面图片 URL；不填则无封面");
        enumProperty(createProps, "status", "draft（默认）或 published", "draft", "draft", "published");
        booleanProperty(createProps, "isTop", "是否置顶", false);
        create.put("inputSchema", schema(mapper, createProps, false, "title", "content"));
        tools.add(create);

        ObjectNode update = tool(mapper, "update_article",
                "修改已有文章。支持只传需要修改的字段；未传的字段保持原样。支持 id 或 slug 定位文章。");
        ObjectNode updateProps = properties(update);
        numberProperty(updateProps, "id", "文章 id", null, 1, null);
        stringProperty(updateProps, "slug", "文章 slug；也可用 id 定位");
        stringProperty(updateProps, "title", "新的标题");
        stringProperty(updateProps, "content", "新的 Markdown 正文");
        stringProperty(updateProps, "summary", "新的摘要");
        stringProperty(updateProps, "newSlug", "新的 URL slug，不传则保持原 slug");
        stringProperty(updateProps, "category", "新的分类名称或 slug");
        arrayProperty(updateProps, "tags", "新的标签名称数组；传空数组表示清空标签");
        stringProperty(updateProps, "coverImage", "新的封面 URL；传空字符串表示移除封面");
        enumProperty(updateProps, "status", "draft 或 published", null, "draft", "published");
        booleanProperty(updateProps, "isTop", "是否置顶", null);
        update.put("inputSchema", schemaWithIdOrSlug(mapper, updateProps));
        tools.add(update);

        ObjectNode publish = tool(mapper, "publish_article",
                "发布或撤回一篇文章。status=published 发布，status=draft 撤回为草稿。");
        ObjectNode publishProps = properties(publish);
        numberProperty(publishProps, "id", "文章 id", null, 1, null);
        stringProperty(publishProps, "slug", "文章 slug；也可用 id 定位");
        enumProperty(publishProps, "status", "目标状态", "published", "draft", "published");
        publish.put("inputSchema", schemaWithIdOrSlug(mapper, publishProps, "status"));
        tools.add(publish);

        ObjectNode delete = tool(mapper, "delete_article",
                "删除一篇文章。必须显式传 confirm=true，删除后评论和文章标签也会一起删除，请谨慎使用。");
        ObjectNode deleteProps = properties(delete);
        numberProperty(deleteProps, "id", "文章 id", null, 1, null);
        stringProperty(deleteProps, "slug", "文章 slug；也可用 id 定位");
        booleanProperty(deleteProps, "confirm", "确认删除，必须为 true", null);
        delete.put("inputSchema", schemaWithIdOrSlug(mapper, deleteProps, "confirm"));
        tools.add(delete);

        return tools;
    }

    private static ObjectNode tool(ObjectMapper mapper, String name, String description) {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        return tool;
    }

    private static ObjectNode properties(ObjectNode tool) {
        ObjectNode input = tool.putObject("inputSchema");
        input.put("type", "object");
        return input.putObject("properties");
    }

    private static ObjectNode schema(ObjectMapper mapper, ObjectNode properties, boolean anyOf, String... required) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", properties);
        ArrayNode requiredNode = schema.putArray("required");
        for (String name : required) {
            requiredNode.add(name);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    private static ObjectNode schema(ObjectMapper mapper, ObjectNode properties, boolean anyOf) {
        return schema(mapper, properties, anyOf, new String[0]);
    }

    private static ObjectNode schemaWithIdOrSlug(ObjectMapper mapper, ObjectNode properties, String... extraRequired) {
        ObjectNode schema = schema(mapper, properties, true, extraRequired);
        ArrayNode anyOf = schema.putArray("anyOf");
        anyOf.addObject().putArray("required").add("id");
        anyOf.addObject().putArray("required").add("slug");
        return schema;
    }

    private static void stringProperty(ObjectNode properties, String name, String description) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "string");
        property.put("description", description);
    }

    private static void enumProperty(ObjectNode properties, String name, String description, String defaultValue,
                                      String... values) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "string");
        property.put("description", description);
        ArrayNode allowed = property.putArray("enum");
        for (String value : values) {
            allowed.add(value);
        }
        if (defaultValue != null) {
            property.put("default", defaultValue);
        }
    }

    private static void numberProperty(ObjectNode properties, String name, String description, Integer defaultValue,
                                       int minimum, Integer maximum) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "integer");
        property.put("description", description);
        property.put("minimum", minimum);
        if (maximum != null) {
            property.put("maximum", maximum);
        }
        if (defaultValue != null) {
            property.put("default", defaultValue);
        }
    }

    private static void booleanProperty(ObjectNode properties, String name, String description, Boolean defaultValue) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "boolean");
        property.put("description", description);
        if (defaultValue != null) {
            property.put("default", defaultValue);
        }
    }

    private static void arrayProperty(ObjectNode properties, String name, String description) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "array");
        property.put("description", description);
        property.putObject("items").put("type", "string");
    }
}
