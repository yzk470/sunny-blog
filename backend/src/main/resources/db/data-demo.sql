-- 晴日小筑 · 演示数据（可重复执行，不会产生重复记录）

INSERT INTO blog_category (name, slug, description, color, sort_order, created_at)
SELECT '生活随笔', 'life', '把平凡的日子，写成亮晶晶的句子。', '#F7B7C8', 1, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM blog_category WHERE slug = 'life');

INSERT INTO blog_category (name, slug, description, color, sort_order, created_at)
SELECT '技术手记', 'tech', '代码、架构，以及那些被解决的小麻烦。', '#8CC8E8', 2, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM blog_category WHERE slug = 'tech');

INSERT INTO blog_category (name, slug, description, color, sort_order, created_at)
SELECT '旅行与远方', 'travel', '去吹吹不同城市的风。', '#A8D8C8', 3, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM blog_category WHERE slug = 'travel');

INSERT INTO blog_category (name, slug, description, color, sort_order, created_at)
SELECT '读书笔记', 'reading', '在别人的故事里，捡到自己的答案。', '#FFD98E', 4, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM blog_category WHERE slug = 'reading');

INSERT INTO blog_tag (name, slug, color, created_at)
SELECT '阳光', 'sunshine', '#FFD86B', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM blog_tag WHERE slug = 'sunshine');

INSERT INTO blog_tag (name, slug, color, created_at)
SELECT '生活', 'life', '#F7B7C8', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM blog_tag WHERE slug = 'life');

INSERT INTO blog_tag (name, slug, color, created_at)
SELECT 'Vue', 'vue', '#9DD5F2', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM blog_tag WHERE slug = 'vue');

INSERT INTO blog_tag (name, slug, color, created_at)
SELECT 'Spring Boot', 'spring-boot', '#B7D9B1', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM blog_tag WHERE slug = 'spring-boot');

INSERT INTO blog_tag (name, slug, color, created_at)
SELECT '旅行', 'travel', '#A8D8D8', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM blog_tag WHERE slug = 'travel');

INSERT INTO blog_tag (name, slug, color, created_at)
SELECT '阅读', 'reading', '#F3C6A5', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM blog_tag WHERE slug = 'reading');

INSERT INTO blog_tag (name, slug, color, created_at)
SELECT '咖啡', 'coffee', '#D9B8A6', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM blog_tag WHERE slug = 'coffee');

INSERT INTO blog_tag (name, slug, color, created_at)
SELECT '摄影', 'photography', '#C9C3F0', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM blog_tag WHERE slug = 'photography');

INSERT INTO blog_post (title, slug, summary, content, cover_image, category_id, status, is_top, view_count, like_count, comment_count, reading_time, created_at, updated_at, published_at)
SELECT '把周末过成一颗糖',
       'weekend-candy',
       '早市的水蜜桃、下午的一杯冰美式、傍晚吹过阳台的风，普通周末也可以被认真收藏。',
       '# 把周末过成一颗糖

周六早上七点半，阳光从窗帘缝里溜进来，在地板上画了一条暖暖的金线。没有闹钟，没有会议，我决定把这个周末过成一颗慢慢融化的水果糖。

> 生活的甜，不一定是大事发生，更多时候只是你愿意为一件小事停下来。

## 早市：把夏天装进布袋

七点五十的早市已经有水汽了。卖桃子的阿姨把水蜜桃码成一座小山，笑着说今天的最甜。我挑了两个，又顺手买了一把薄荷。

- 水蜜桃两枚
- 薄荷一把
- 刚出炉的葱油饼
- 一束十块钱的小雏菊

回家路上，阳光把树影剪成一块一块。我突然觉得，能认真逛一次早市，就是很了不起的浪漫。

## 下午：一杯冰美式的时间

下午三点，咖啡馆里放着轻轻的 City Pop。我坐在靠窗的位置，把上周没读完的书翻到夹着书签的那一页。

```java
public class Weekend {
    public static void main(String[] args) {
        System.out.println("今天也要开心");
    }
}
```

写代码和喝咖啡其实很像：都需要一点耐心，也都允许你偶尔发一会儿呆。

## 晚上：把日落收进相册

六点半，天边从奶油色变成淡粉色。我端着刚做好的薄荷气泡水站到阳台上，看楼下的猫慢慢走过。

今天没有完成什么宏大的目标，但我记住了桃子很甜、风很轻、晚霞很好看。

这样就很好。',
       '/covers/morning.svg',
       (SELECT id FROM blog_category WHERE slug = 'life'),
       1, TRUE, 328, 42, 2, 4,
       '2025-05-18 09:20:00', '2025-05-18 09:20:00', '2025-05-18 09:20:00'
WHERE NOT EXISTS (SELECT 1 FROM blog_post WHERE slug = 'weekend-candy');

INSERT INTO blog_post (title, slug, summary, content, cover_image, category_id, status, is_top, view_count, like_count, comment_count, reading_time, created_at, updated_at, published_at)
SELECT '在春天，重新学会散步',
       'spring-walk',
       '不带耳机，不看步数，沿着河堤走到哪里算哪里。春天教我的第一件事，是慢一点也没关系。',
       '# 在春天，重新学会散步

三月的一个下午，我把耳机留在家里，决定出门随便走走。

## 不带目的地的出发

从小区后门出去，沿着河堤一直走。柳树刚冒出很浅的绿，风里有一点潮湿的泥土味。以前散步总想走够一万步，现在觉得，走到哪里算哪里也很好。

路过一座小桥时，我看见有人蹲在岸边拍一朵野花。那一刻我停下来，第一次认真看那朵花：花瓣是半透明的白，边缘有一点点粉。

## 春天的小发现

- 河边的玉兰开了，像一只只停在枝头的小鸟
- 便利店门口多了草莓味的冰淇淋
- 一位爷爷在教孙子放风筝，风筝卡在树上，两个人一起笑
- 夕阳落下来时，整条河都变成了橘子汽水

## 慢一点也没关系

这个时代总在提醒我们要更快：更快完成、更快抵达、更快成为更好的人。但春天不着急，它一片叶子一片叶子地长。

我想我也应该允许自己，偶尔只是走路，只是看花，只是在风里站一会儿。

回家时天已经黑了，走了八千多步。没有破纪录，但捡到了一整个下午的好心情。',
       '/covers/spring.svg',
       (SELECT id FROM blog_category WHERE slug = 'life'),
       1, FALSE, 196, 26, 0, 3,
       '2025-04-06 15:40:00', '2025-04-06 15:40:00', '2025-04-06 15:40:00'
WHERE NOT EXISTS (SELECT 1 FROM blog_post WHERE slug = 'spring-walk');

INSERT INTO blog_post (title, slug, summary, content, cover_image, category_id, status, is_top, view_count, like_count, comment_count, reading_time, created_at, updated_at, published_at)
SELECT 'Vue 3 + TypeScript：让组件像阳光一样清晰',
       'vue3-typescript-sunny',
       '从 script setup 到组合式函数，记录一次把 Vue 3 项目写得更轻、更稳、更好维护的实践。',
       '# Vue 3 + TypeScript：让组件像阳光一样清晰

最近把一个老项目迁到了 Vue 3 + TypeScript。迁移完成后最大的感受不是语法变新了，而是组件之间的边界终于像晴天的窗玻璃一样清楚。

## 先拥抱 script setup

`<script setup lang="ts">` 让组件的逻辑更紧凑，类型推导也更自然。

```vue
<script setup lang="ts">
import { computed, ref } from "vue";

interface Post {
  id: number;
  title: string;
  tags: string[];
}

const posts = ref<Post[]>([]);
const keyword = ref("");

const filtered = computed(() =>
  posts.value.filter((post) => post.title.includes(keyword.value))
);
</script>
```

## 把可复用的逻辑抽成 composable

请求、分页、加载状态这些逻辑，很适合从组件里抽出来。

```ts
export function usePagination(initialPage = 1, initialSize = 10) {
  const page = ref(initialPage);
  const size = ref(initialSize);
  const total = ref(0);

  const pages = computed(() => Math.ceil(total.value / size.value));
  const next = () => {
    if (page.value < pages.value) page.value += 1;
  };

  return { page, size, total, pages, next };
}
```

## 类型不是负担，是路标

- 给 API 返回值定义 interface，接口改动时编译器会提醒你
- 用 `PropType` 明确组件 props 的类型
- 避免到处使用 `any`，它会悄悄把问题藏起来
- 类型推断优先于手写类型，让编译器多做一点

## 一点小提醒

TypeScript 不会自动让代码变好，它只是把一些错误提前摆到桌面上。真正的清晰，还是来自对业务边界的思考。

就像收拾房间：收纳盒很有用，但前提是你知道每样东西应该放在哪里。',
       '/covers/code.svg',
       (SELECT id FROM blog_category WHERE slug = 'tech'),
       1, TRUE, 512, 78, 1, 6,
       '2025-03-22 10:10:00', '2025-03-22 10:10:00', '2025-03-22 10:10:00'
WHERE NOT EXISTS (SELECT 1 FROM blog_post WHERE slug = 'vue3-typescript-sunny');

INSERT INTO blog_post (title, slug, summary, content, cover_image, category_id, status, is_top, view_count, like_count, comment_count, reading_time, created_at, updated_at, published_at)
SELECT 'Spring Boot 缓存手记：把常用数据放在手边',
       'springboot-redis-cache',
       '用 Redis 给博客做缓存：缓存什么、什么时候失效、缓存挂了怎么办，一次讲清楚。',
       '# Spring Boot 缓存手记：把常用数据放在手边

博客里有一类数据：读得多、改得少。分类、标签、站点资料、文章详情，都很适合放进 Redis。

## 缓存不是越多越好

我给自己定了三个原则：

1. 热点数据优先，例如首页文章和文章详情
2. 接受短暂不一致，例如站点资料晚半分钟更新
3. 缓存失败不能影响主流程，数据库永远是兜底

## 给缓存加一个版本号

相比在更新时扫描并删除一堆 key，我更喜欢给每个命名空间加版本号：

```text
sunnyblog:postDetail:3:weekend-candy
```

文章更新时，只需要把 `postDetail` 的版本号加一，旧 key 会自然过期。这样既避免了 KEYS 扫描，也让失效逻辑足够简单。

## 缓存读取的伪代码

```java
public PostDetailVO getDetail(String slug) {
    PostDetailVO cached = cache.get("postDetail", slug, DETAIL_TYPE);
    if (cached != null) {
        return cached;
    }
    PostDetailVO detail = loadFromDatabase(slug);
    cache.put("postDetail", slug, detail, Duration.ofMinutes(10));
    return detail;
}
```

## Redis 挂了怎么办

这是最容易被忽略的问题。我的处理方式是把缓存读写都包在 try-catch 里，失败时记录日志并退回数据库查询。

阅读量也做了类似设计：先写 Redis 的 Hash，定时批量回写数据库；Redis 不在时，直接更新数据库。

> 缓存是加速器，不是发动机。发动机永远应该是数据库。

## 最后

缓存策略没有标准答案，重要的是知道自己为什么缓存、能容忍多久的不一致，以及系统在缓存失效时还能不能好好工作。',
       '/covers/code.svg',
       (SELECT id FROM blog_category WHERE slug = 'tech'),
       1, FALSE, 407, 55, 0, 5,
       '2025-03-02 20:30:00', '2025-03-02 20:30:00', '2025-03-02 20:30:00'
WHERE NOT EXISTS (SELECT 1 FROM blog_post WHERE slug = 'springboot-redis-cache');

INSERT INTO blog_post (title, slug, summary, content, cover_image, category_id, status, is_top, view_count, like_count, comment_count, reading_time, created_at, updated_at, published_at)
SELECT '去海边吧，带上没写完的清单',
       'seaside-list',
       '一场说走就走的海边短途：赶海、看日出、在小馆子里吃一碗热汤面。',
       '# 去海边吧，带上没写完的清单

出发前夜，我在笔记本上写了一份海边清单。写到一半就困了，于是带着半张纸去了车站。

## 清单上的事

- 看一次日出
- 光脚踩在沙滩上
- 吃一碗当地的热汤面
- 捡一枚好看的贝壳
- 什么都不做地坐十分钟

## 没写上的事

凌晨四点半被闹钟叫醒，天还是深蓝色。走到海边时，已经有三三两两的人在等日出。海风比想象中凉，我把外套裹紧了一点。

太阳出来的时候，没有人说话。海面先是浅浅的橘，然后一整片亮起来。那一刻我突然觉得，所有早起都值得。

## 海边的慢时间

白天的小镇很安静。路边晾着渔网，猫在台阶上睡觉。中午随便进了一家小馆子，老板娘端来一碗热气腾腾的海鲜面。

下午坐在防波堤上，什么也没做。看潮水一遍遍涌上来，又退回去。

## 带回来的

一枚白色的小贝壳，一点晒红的鼻尖，还有一张没完成的清单。

没关系，下次再来把它写完。',
       '/covers/sea.svg',
       (SELECT id FROM blog_category WHERE slug = 'travel'),
       1, FALSE, 289, 34, 0, 4,
       '2025-02-14 08:15:00', '2025-02-14 08:15:00', '2025-02-14 08:15:00'
WHERE NOT EXISTS (SELECT 1 FROM blog_post WHERE slug = 'seaside-list');

INSERT INTO blog_post (title, slug, summary, content, cover_image, category_id, status, is_top, view_count, like_count, comment_count, reading_time, created_at, updated_at, published_at)
SELECT '一间咖啡馆的午后观察',
       'cafe-afternoon',
       '靠窗的位置、磨豆机的声音、陌生人写下的便签。咖啡馆是城市的客厅。',
       '# 一间咖啡馆的午后观察

家附近新开了一间咖啡馆，奶油色的墙，浅蓝色的门。我第一次进去，是因为门口写着一行小字：今天也要好好休息。

## 下午两点的座位

我选了靠窗的位置。阳光正好落在桌角，杯子的影子被拉得很长。

左边的女生在改简历，右边的大叔在看报纸，吧台后的店员慢慢压着手冲。磨豆机响一下，又安静下来。

## 陌生人留下的便签

墙上贴满了顾客写的便签：

> 希望明年可以去冰岛看极光。

> 今天没有哭，值得表扬。

> 考研加油，考完来喝最贵的咖啡。

我看了很久，觉得这些短短的句子，比很多长篇大论都更有力量。

## 一杯叫晴天的特调

店员推荐了一杯叫晴天的特调：柑橘、气泡水、一点点海盐。喝起来像把窗户打开了。

我在那里坐了两个小时，改完一段文案，写完三页笔记，也发了一会儿呆。

## 城市里的客厅

咖啡馆的好处，是允许你和陌生人共享一段安静的时间。不需要交谈，也不必认识，只要各自坐一会儿，就已经足够温柔。

离开时，我在便签上写：希望下一次来，还是好天气。',
       '/covers/cafe.svg',
       (SELECT id FROM blog_category WHERE slug = 'life'),
       1, FALSE, 173, 21, 0, 3,
       '2025-01-20 14:00:00', '2025-01-20 14:00:00', '2025-01-20 14:00:00'
WHERE NOT EXISTS (SELECT 1 FROM blog_post WHERE slug = 'cafe-afternoon');

INSERT INTO blog_post (title, slug, summary, content, cover_image, category_id, status, is_top, view_count, like_count, comment_count, reading_time, created_at, updated_at, published_at)
SELECT '读《山茶文具店》：慢慢写字，慢慢生活',
       'camellia-stationery',
       '一封封代笔的信，连接起人与人之间说不出口的心意。读完想认真写一封信。',
       '# 读《山茶文具店》：慢慢写字，慢慢生活

这本书讲的是镰仓的一间文具店，店主替人代笔，写下那些难以亲口说出口的话。

## 手写字的温度

书里最打动我的是那些写信的细节：用什么样的纸、什么样的墨、什么样的邮票，甚至信封封口的方式，都要根据收信人和内容来决定。

在即时通讯的时代，我们很少再为一句话挑选一张纸。但也正因为如此，手写信才显得珍贵。

## 几个喜欢的片段

> 只要在心里说谢谢，就会在不知不觉中传达给对方。

> 与其思考怎样写得漂亮，不如思考怎样写得真诚。

书里的每一封信都不长，却都像一杯温热的茶，慢慢喝完，心也慢慢安静下来。

## 读完想做的事

- 给很久没联系的朋友写一张明信片
- 买一瓶味道好闻的墨水
- 把想说的话，认真写下来，而不是只打在对话框里

## 慢慢来

这本书没有跌宕起伏的情节，却让人愿意放慢速度。它提醒我，生活里有些事急不得：写字急不得，道歉急不得，喜欢一个人也急不得。

慢慢来，反而比较快。',
       '/covers/reading.svg',
       (SELECT id FROM blog_category WHERE slug = 'reading'),
       1, FALSE, 241, 39, 0, 4,
       '2024-12-08 21:10:00', '2024-12-08 21:10:00', '2024-12-08 21:10:00'
WHERE NOT EXISTS (SELECT 1 FROM blog_post WHERE slug = 'camellia-stationery');

INSERT INTO blog_post (title, slug, summary, content, cover_image, category_id, status, is_top, view_count, like_count, comment_count, reading_time, created_at, updated_at, published_at)
SELECT '2024 年终总结：收集了 365 个晴天',
       'year-2024-summary',
       '这一年没有惊天动地，但有很多值得记住的小事。把它们收好，然后继续往前走。',
       '# 2024 年终总结：收集了 365 个晴天

又到年末。翻相册的时候发现，今年拍了很多天空：清晨的、傍晚的、下雨前的、放晴后的。

## 今年完成的小事

- 坚持写了 36 篇博客
- 学会做三道拿手菜
- 去了一次海边
- 读完 18 本书
- 把阳台变成了一个小花园

## 今年学会的事

### 1. 允许自己慢一点

不是每一天都要高效。有些下午就是用来晒太阳的。

### 2. 记录比记忆可靠

很多当时觉得不会忘的瞬间，后来真的忘了。幸好还有照片、文字和便签。

### 3. 好好吃饭，好好睡觉

这八个字看起来简单，做到却不容易。新的一年继续练习。

## 写给 2025

希望明年的我，依然对世界保持好奇，依然愿意为一场晚霞停下车，依然有几个可以随时说话的朋友。

也希望能写出更多真诚的文字。

> 愿新年的每一天，都有阳光落在肩上。

2025，请多关照。',
       '/covers/sunset.svg',
       (SELECT id FROM blog_category WHERE slug = 'life'),
       1, FALSE, 356, 48, 0, 4,
       '2024-12-31 22:00:00', '2024-12-31 22:00:00', '2024-12-31 22:00:00'
WHERE NOT EXISTS (SELECT 1 FROM blog_post WHERE slug = 'year-2024-summary');

INSERT INTO blog_post (title, slug, summary, content, cover_image, category_id, status, is_top, view_count, like_count, comment_count, reading_time, created_at, updated_at, published_at)
SELECT '下一篇：夏日汽水计划',
       'summer-soda-plan',
       '一篇还在草稿箱里的夏日计划：做梅子汽水、去看萤火虫、把阳台刷成浅蓝色。',
       '# 夏日汽水计划（草稿）

这篇还没有写完，先记下几个想在夏天完成的小愿望。

- 自己腌一罐青梅
- 做一杯加薄荷的梅子汽水
- 去山里看一次萤火虫
- 给阳台刷上浅蓝色
- 学会一支完整的舞

等夏天到了，再一条一条划掉。',
       '/covers/spring.svg',
       (SELECT id FROM blog_category WHERE slug = 'life'),
       0, FALSE, 0, 0, 0, 1,
       '2025-06-01 11:00:00', '2025-06-01 11:00:00', NULL
WHERE NOT EXISTS (SELECT 1 FROM blog_post WHERE slug = 'summer-soda-plan');

-- 文章与标签
INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'weekend-candy' AND t.slug = 'life'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'weekend-candy' AND t.slug = 'sunshine'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'weekend-candy' AND t.slug = 'coffee'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'spring-walk' AND t.slug = 'life'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'spring-walk' AND t.slug = 'photography'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'vue3-typescript-sunny' AND t.slug = 'vue'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'vue3-typescript-sunny' AND t.slug = 'spring-boot'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'springboot-redis-cache' AND t.slug = 'spring-boot'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'springboot-redis-cache' AND t.slug = 'vue'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'seaside-list' AND t.slug = 'travel'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'seaside-list' AND t.slug = 'sunshine'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'cafe-afternoon' AND t.slug = 'coffee'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'cafe-afternoon' AND t.slug = 'life'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'camellia-stationery' AND t.slug = 'reading'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'year-2024-summary' AND t.slug = 'life'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'year-2024-summary' AND t.slug = 'sunshine'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

INSERT INTO blog_post_tag (post_id, tag_id)
SELECT p.id, t.id FROM blog_post p, blog_tag t
WHERE p.slug = 'summer-soda-plan' AND t.slug = 'life'
  AND NOT EXISTS (SELECT 1 FROM blog_post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = t.id);

-- 评论
INSERT INTO blog_comment (post_id, author, email, website, content, status, is_admin, parent_id, created_at)
SELECT p.id, '小满', 'xiaoman@example.com', NULL, '看完也想认真过周末了，桃子看起来好甜！', 1, FALSE, NULL, '2025-05-18 11:02:00'
FROM blog_post p
WHERE p.slug = 'weekend-candy'
  AND NOT EXISTS (SELECT 1 FROM blog_comment c WHERE c.post_id = p.id AND c.author = '小满');

INSERT INTO blog_comment (post_id, author, email, website, content, status, is_admin, parent_id, created_at)
SELECT p.id, '月白', 'hello@sunny.blog', NULL, '下次来杭州，请你喝薄荷气泡水。', 1, TRUE, NULL, '2025-05-18 12:15:00'
FROM blog_post p
WHERE p.slug = 'weekend-candy'
  AND NOT EXISTS (SELECT 1 FROM blog_comment c WHERE c.post_id = p.id AND c.author = '月白');

INSERT INTO blog_comment (post_id, author, email, website, content, status, is_admin, parent_id, created_at)
SELECT p.id, '阿树', 'ashu@example.com', 'https://example.com', '类型推导那一段很受用，准备把项目里的 any 都清一遍。', 1, FALSE, NULL, '2025-03-22 16:40:00'
FROM blog_post p
WHERE p.slug = 'vue3-typescript-sunny'
  AND NOT EXISTS (SELECT 1 FROM blog_comment c WHERE c.post_id = p.id AND c.author = '阿树');

INSERT INTO blog_comment (post_id, author, email, website, content, status, is_admin, parent_id, created_at)
SELECT p.id, '路过的风', 'wind@example.com', NULL, '请问这篇文章可以转载吗？会注明出处。', 0, FALSE, NULL, '2025-06-02 09:30:00'
FROM blog_post p
WHERE p.slug = 'springboot-redis-cache'
  AND NOT EXISTS (SELECT 1 FROM blog_comment c WHERE c.post_id = p.id AND c.author = '路过的风');

-- 留言板
INSERT INTO blog_guestbook (author, email, content, reply, status, created_at)
SELECT '小橘子', 'orange@example.com', '博客的颜色好温柔，像晒过太阳的被子。', '谢谢喜欢，祝你今天也有好心情。', 1, '2025-05-20 10:20:00'
WHERE NOT EXISTS (SELECT 1 FROM blog_guestbook WHERE author = '小橘子');

INSERT INTO blog_guestbook (author, email, content, reply, status, created_at)
SELECT '北岛', 'beidao@example.com', '从搜索引擎找过来的，文章写得很舒服，已收藏。', NULL, 1, '2025-04-18 19:45:00'
WHERE NOT EXISTS (SELECT 1 FROM blog_guestbook WHERE author = '北岛');

INSERT INTO blog_guestbook (author, email, content, reply, status, created_at)
SELECT 'Momo', 'momo@example.com', '希望博主多更新技术文章，蹲一个 Spring Boot 系列。', NULL, 1, '2025-03-30 22:10:00'
WHERE NOT EXISTS (SELECT 1 FROM blog_guestbook WHERE author = 'Momo');

INSERT INTO blog_guestbook (author, email, content, reply, status, created_at)
SELECT '新朋友', 'new@example.com', '第一次来，想问一下博客的插图是自己画的吗？', NULL, 0, '2025-06-05 08:00:00'
WHERE NOT EXISTS (SELECT 1 FROM blog_guestbook WHERE author = '新朋友');

-- 站点资料
INSERT INTO blog_site_profile (nickname, avatar, bio, slogan, location, email, github, about_content, footer_text, updated_at)
SELECT '月白',
       '/images/avatar-yuebai.png',
       '白天写代码，傍晚追晚霞。在代码与云朵之间，收集每一个亮晶晶的日子。',
       '把日子过成一首明亮的诗',
       '杭州',
       'hello@sunny.blog',
       'https://github.com/',
       '# 嗨，我是月白

白天写代码，傍晚追晚霞，偶尔在厨房里研究一杯不太成功的拿铁。

这个小小的博客，用来存放生活里的光：一段散步、一篇文章、一次远行，以及那些突然想明白的小事。

## 关于我

- 一名喜欢把界面做得温柔一点的前端工程师
- 正在学习如何把后端也写得同样干净
- 相信好的技术应该让人更从容，而不是更焦虑

## 这里会有什么

- 生活里值得记住的小事
- Vue、TypeScript、Spring Boot 的实践笔记
- 读过的书、去过的地方
- 一些没什么用但很开心的小发现

## 联系我

如果你也想聊聊天，欢迎在留言板留下足迹，或者写信到 hello@sunny.blog。

> 愿我们都能在普通的日子里，找到属于自己的光。',
       '愿你的每一天，都有阳光落在肩上。',
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM blog_site_profile);
