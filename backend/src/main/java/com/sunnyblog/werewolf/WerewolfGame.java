package com.sunnyblog.werewolf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One self-contained werewolf game. The engine is deterministic per instance
 * and deliberately uses lightweight rule-based agents instead of remote LLM
 * calls, so a game can be started, played and auto-completed without an API key.
 */
public class WerewolfGame {

    public enum Team {
        WEREWOLF("狼人阵营"), VILLAGER("好人阵营");
        private final String label;
        Team(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum Role {
        WEREWOLF("狼人", Team.WEREWOLF, "🐺", "每晚与狼队友共同袭击一名玩家"),
        VILLAGER("村民", Team.VILLAGER, "🌾", "没有特殊能力，通过发言和投票找出狼人"),
        SEER("预言家", Team.VILLAGER, "🔮", "每晚查验一名玩家的阵营"),
        WITCH("女巫", Team.VILLAGER, "🧪", "拥有一瓶解药和一瓶毒药，每晚最多使用一瓶"),
        HUNTER("猎人", Team.VILLAGER, "🏹", "被狼人杀害或被投票出局时可以开枪带走一人");

        private final String label;
        private final Team team;
        private final String icon;
        private final String description;

        Role(String label, Team team, String icon, String description) {
            this.label = label;
            this.team = team;
            this.icon = icon;
            this.description = description;
        }

        public String getLabel() { return label; }
        public Team getTeam() { return team; }
        public String getIcon() { return icon; }
        public String getDescription() { return description; }
        public boolean isWerewolf() { return team == Team.WEREWOLF; }
    }

    public enum Phase {
        NIGHT_WOLF("狼人行动", "夜色笼罩小镇，狼人正在选择目标"),
        NIGHT_WITCH_SAVE("女巫解药", "女巫正在决定是否救人"),
        NIGHT_WITCH_POISON("女巫毒药", "女巫正在决定是否用毒"),
        NIGHT_SEER("预言家查验", "预言家正在查验一名玩家"),
        DAWN("天亮了", "昨夜的结果即将揭晓"),
        DISCUSSION("白天讨论", "玩家们交换信息，寻找可疑的人"),
        VOTE("放逐投票", "投票决定今天放逐谁"),
        HUNTER_SHOOT("猎人开枪", "猎人倒下了，但枪还在手里"),
        GAME_OVER("游戏结束", "胜负已分");

        private final String label;
        private final String hint;
        Phase(String label, String hint) { this.label = label; this.hint = hint; }
        public String getLabel() { return label; }
        public String getHint() { return hint; }
    }

    public static class Player {
        private final int seat;
        private final String id;
        private final String name;
        private final Role role;
        private final boolean human;
        private boolean alive = true;
        private String deathCause;
        private boolean revealed;
        private String claimedRole;
        private int suspicion;
        private boolean antidote;
        private boolean poison;
        private boolean canShoot;

        Player(int seat, String id, String name, Role role, boolean human) {
            this.seat = seat;
            this.id = id;
            this.name = name;
            this.role = role;
            this.human = human;
            this.antidote = role == Role.WITCH;
            this.poison = role == Role.WITCH;
            this.canShoot = role == Role.HUNTER;
        }

        public int getSeat() { return seat; }
        public String getId() { return id; }
        public String getName() { return name; }
        public Role getRole() { return role; }
        public boolean isHuman() { return human; }
        public boolean isAlive() { return alive; }
        public String getDeathCause() { return deathCause; }
        public boolean isRevealed() { return revealed; }
        public String getClaimedRole() { return claimedRole; }
        public int getSuspicion() { return suspicion; }
        public boolean hasAntidote() { return antidote; }
        public boolean hasPoison() { return poison; }
        public boolean hasCanShoot() { return canShoot; }
    }

    public record Option(String id, String label) {}

    public static class Waiting {
        private final String type;
        private final String title;
        private final String prompt;
        private final List<Option> options;

        Waiting(String type, String title, String prompt, List<Option> options) {
            this.type = type;
            this.title = title;
            this.prompt = prompt;
            this.options = options;
        }

        public String getType() { return type; }
        public String getTitle() { return title; }
        public String getPrompt() { return prompt; }
        public List<Option> getOptions() { return options; }
    }

    public record LogEntry(int seq, int round, String phase, String phaseLabel, String type, String text, String privateTo) {}
    public record LogView(int seq, int round, String phase, String phaseLabel, String type, String text, boolean privateLog) {}
    public record AbilitiesView(boolean antidote, boolean poison, boolean canShoot) {}
    public record PlayerView(
            String id, int seat, String name, boolean alive, boolean human,
            String role, String roleLabel, String roleIcon, String team,
            String deathCause, String claimedRole, String claimedRoleLabel, int suspicion
    ) {}
    public record WaitingView(String type, String title, String prompt, List<Option> options) {}
    public record GameView(
            String id, int round, String phase, String phaseLabel, String phaseHint,
            boolean over, String winner, String winnerLabel, String humanId, boolean humanAlive,
            String myRole, String myRoleLabel, String myRoleIcon, String myTeam, String myRoleDescription,
            List<String> teammates, AbilitiesView abilities, WaitingView waiting,
            List<PlayerView> players, List<LogView> logs, String summary
    ) {}

    private static final String[] AI_NAMES = {
            "阿澈", "小满", "鹿鸣", "南枝", "青禾", "望舒", "星野", "白露",
            "山岚", "云舟", "木槿", "拾光", "知夏", "临川", "晚棠", "听澜"
    };
    private static final String[] SPEECH_OPENERS = {
            "我先说说我的看法。", "这一轮信息还不多。", "我一直在听大家的发言。",
            "从昨晚的结果看，", "我有个不太成熟的判断，", "先别急着投票，"
    };

    private final String id;
    private final int playerCount;
    private final Random random;
    private final List<Player> players = new ArrayList<>();
    private final List<LogEntry> logs = new ArrayList<>();
    private final Map<String, Role> seerChecks = new LinkedHashMap<>();
    private final Map<String, Integer> publicSuspicion = new HashMap<>();
    private final Set<String> publicWolfIds = new HashSet<>();

    private String humanId;
    private int round;
    private Phase phase = Phase.NIGHT_WOLF;
    private boolean over;
    private String winner;
    private int logSeq;
    private Waiting waiting;

    private String wolfTarget;
    private String witchSaveTarget;
    private String witchPoisonTarget;
    private boolean witchPotionUsedThisNight;
    private String seerCheckTarget;
    private Role seerCheckResult;
    private String humanVoteTarget;
    private String pendingHunterId;
    private boolean hunterFromVote;
    private boolean seerClaimed;
    private String claimedSeerId;

    public WerewolfGame(String playerName, int requestedPlayers, Role preferredRole) {
        this.id = UUID.randomUUID().toString().replace("-", "");
        this.playerCount = Math.max(6, Math.min(12, requestedPlayers));
        this.random = new Random();
        setupPlayers(playerName, preferredRole);
        log(null, "system", "欢迎来到月白小镇。本局共 " + playerCount + " 位玩家，你是 " + human().getName() + "。");
        log(humanId, "role", "你的身份是【" + human().getRole().getLabel() + "】。" + human().getRole().getDescription());
        if (human().getRole() == Role.WEREWOLF) {
            String mates = alivePlayers().stream()
                    .filter(p -> p.getRole() == Role.WEREWOLF && !p.getId().equals(humanId))
                    .map(Player::getName)
                    .collect(Collectors.joining("、"));
            if (!mates.isEmpty()) log(humanId, "role", "你的狼队友是：" + mates + "。");
        }
        beginNight();
    }

    /**
     * Independent model calls for each AI role. Every method receives the game
     * so the provider can build a context containing only what that role knows.
     */
    public interface DecisionProvider {
        String chooseWolfTarget(WerewolfGame game);
        String chooseSeerCheck(WerewolfGame game, Player seer);
        Boolean chooseWitchSave(WerewolfGame game, Player witch, Player victim);
        String chooseWitchPoison(WerewolfGame game, Player witch);
        Map<String, String> chooseVotes(WerewolfGame game, List<Player> voters);
        String chooseHunterShot(WerewolfGame game, Player hunter);
    }

    /** Lets the SSE endpoint mirror every table event live, not only speeches. */
    public interface GameEventListener {
        void onLog(LogEntry entry);
        void onSnapshot(GameView view);
        void onThinking(String text);
    }

    private DecisionProvider decisionProvider;
    private GameEventListener eventListener;
    private final List<String> discussionQueue = new ArrayList<>();
    private final Set<String> discussionSpoken = new HashSet<>();
    private boolean discussionReady;

    public void setDecisionProvider(DecisionProvider decisionProvider) {
        this.decisionProvider = decisionProvider;
    }

    public void setEventListener(GameEventListener eventListener) {
        this.eventListener = eventListener;
    }

    private void notifyThinking(String text) {
        if (eventListener != null) {
            try {
                eventListener.onThinking(text);
            } catch (Exception ignored) {
                // stream closed
            }
        }
    }

    private void notifySnapshot() {
        if (eventListener != null) {
            try {
                eventListener.onSnapshot(toView());
            } catch (Exception ignored) {
                // stream closed
            }
        }
    }

    public List<Player> allPlayers() {
        return List.copyOf(players);
    }

    public List<Player> aliveAiPlayers() {
        return alivePlayers().stream().filter(p -> !p.isHuman()).toList();
    }

    public List<Player> alivePlayersView() {
        return List.copyOf(alivePlayers());
    }

    public List<Player> aliveWerewolvesView() {
        return List.copyOf(aliveWerewolves());
    }

    public List<Player> aliveGoodView() {
        return List.copyOf(aliveGood());
    }

    public Player humanPlayer() {
        return human();
    }

    public Map<String, Integer> suspicionView() {
        return Map.copyOf(publicSuspicion);
    }

    public Map<String, Role> seerChecksView() {
        return Map.copyOf(seerChecks);
    }

    public String currentWolfTarget() {
        return wolfTarget;
    }

    public int getRound() {
        return round;
    }

    public Phase getPhase() {
        return phase;
    }

    public String publicStateText() {
        GameView view = toView();
        return view.logs().stream()
                .filter(l -> !l.privateLog())
                .skip(Math.max(0, view.logs().size() - 26L))
                .map(l -> "[" + l.phaseLabel() + "] " + l.text())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Builds the private knowledge packet for one agent. This is the only context
     * a model prompt should receive for that agent.
     */
    public String contextFor(Player viewer) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 ").append(viewer.getName()).append("，身份【").append(viewer.getRole().getLabel())
                .append("】，阵营：").append(viewer.getRole().getTeam().getLabel()).append("。\n");
        sb.append("当前第 ").append(round).append(" 轮，阶段：").append(phase.getLabel()).append("。\n");
        sb.append("存活玩家：\n");
        for (Player p : alivePlayers()) {
            sb.append("- ").append(p.getName()).append("(").append(p.getId()).append(")")
                    .append(p.isHuman() ? " [人类]" : "")
                    .append(p.getClaimedRole() == null ? "" : " [自称" + p.getClaimedRole() + "]")
                    .append("\n");
        }
        if (viewer.getRole() == Role.WEREWOLF) {
            String mates = alivePlayers().stream()
                    .filter(p -> p.getRole() == Role.WEREWOLF && !p.getId().equals(viewer.getId()))
                    .map(Player::getName)
                    .collect(Collectors.joining("、"));
            sb.append("你的狼队友：").append(mates.isEmpty() ? "无" : mates).append("。\n");
        } else if (viewer.getRole() == Role.SEER) {
            sb.append("你的查验记录：\n");
            if (seerChecks.isEmpty()) sb.append("- 还没有查验记录\n");
            else {
                for (Map.Entry<String, Role> e : seerChecks.entrySet()) {
                    Player p = player(e.getKey());
                    sb.append("- ").append(p == null ? e.getKey() : p.getName())
                            .append("：").append(e.getValue().getLabel()).append("\n");
                }
            }
        } else if (viewer.getRole() == Role.WITCH) {
            sb.append("你的药剂：解药 ").append(viewer.hasAntidote() ? "可用" : "已用")
                    .append("，毒药 ").append(viewer.hasPoison() ? "可用" : "已用").append("。\n");
            if (wolfTarget != null) {
                Player victim = player(wolfTarget);
                sb.append("今晚被狼人袭击的是：").append(victim == null ? "未知" : victim.getName()).append("。\n");
            }
        } else if (viewer.getRole() == Role.HUNTER) {
            sb.append("你的猎枪：").append(viewer.hasCanShoot() ? "可以开枪" : "已使用").append("。\n");
        }
        sb.append("\n公开事件与发言：\n").append(publicStateText());
        return sb.toString();
    }

    public boolean hasPendingDiscussion() {
        return phase == Phase.DISCUSSION && !discussionQueue.isEmpty();
    }

    public Player nextDiscussionSpeaker() {
        while (!discussionQueue.isEmpty()) {
            Player p = player(discussionQueue.get(0));
            if (p != null && p.isAlive() && !discussionSpoken.contains(p.getId())) return p;
            discussionQueue.remove(0);
        }
        return null;
    }

    public void recordSpeech(String playerId, String text) {
        Player speaker = player(playerId);
        if (speaker == null || !speaker.isAlive() || text == null || text.isBlank()) return;
        discussionQueue.remove(playerId);
        discussionSpoken.add(playerId);
        log(null, "speech", speaker.getName() + "：" + text.trim());
    }

    public void finishDiscussion() {
        if (phase != Phase.DISCUSSION) return;
        log(null, "system", "讨论结束，进入放逐投票。");
        discussionQueue.clear();
        discussionSpoken.clear();
        discussionReady = false;
        phase = Phase.VOTE;
        notifySnapshot();
    }

    public void autoDiscussRemaining() {
        while (hasPendingDiscussion()) {
            Player speaker = nextDiscussionSpeaker();
            if (speaker == null) break;
            recordSpeech(speaker.getId(), generateSpeech(speaker));
        }
        finishDiscussion();
    }

    public boolean hasWaiting() {
        return waiting != null;
    }

    public void autoResolveWaiting() {
        if (waiting == null) return;
        autoResolveWaitingInternal();
    }

    public String fallbackSpeech(Player speaker) {
        return generateSpeech(speaker);
    }

    // ─────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────

    private void setupPlayers(String playerName, Role preferredRole) {
        int wolves = playerCount >= 10 ? 3 : 2;
        int hunter = playerCount >= 8 ? 1 : 0;
        int villagers = playerCount - wolves - 2 - hunter;

        List<Role> rolePool = new ArrayList<>();
        for (int i = 0; i < wolves; i++) rolePool.add(Role.WEREWOLF);
        rolePool.add(Role.SEER);
        rolePool.add(Role.WITCH);
        if (hunter > 0) rolePool.add(Role.HUNTER);
        for (int i = 0; i < villagers; i++) rolePool.add(Role.VILLAGER);
        Collections.shuffle(rolePool, random);

        Role humanRole = null;
        if (preferredRole != null && rolePool.remove(preferredRole)) {
            humanRole = preferredRole;
        }
        if (humanRole == null) {
            humanRole = rolePool.remove(rolePool.size() - 1);
        }

        String cleanName = playerName == null ? "" : playerName.trim();
        if (cleanName.isEmpty()) cleanName = "月白";
        if (cleanName.length() > 12) cleanName = cleanName.substring(0, 12);

        List<String> names = new ArrayList<>();
        for (String name : AI_NAMES) {
            if (names.size() >= playerCount - 1) break;
            if (!name.equals(cleanName)) names.add(name);
        }
        Collections.shuffle(names, random);

        humanId = "p1";
        players.add(new Player(1, humanId, cleanName, humanRole, true));
        for (int i = 1; i < playerCount; i++) {
            String pid = "p" + (i + 1);
            players.add(new Player(i + 1, pid, names.get(i - 1), rolePool.remove(rolePool.size() - 1), false));
        }
        for (Player p : players) {
            publicSuspicion.put(p.getId(), random.nextInt(3));
        }
    }

    private Player human() {
        return players.stream().filter(Player::isHuman).findFirst().orElse(players.get(0));
    }

    private List<Player> alivePlayers() {
        return players.stream().filter(Player::isAlive).toList();
    }

    private List<Player> aliveWerewolves() {
        return alivePlayers().stream().filter(p -> p.getRole().isWerewolf()).toList();
    }

    private List<Player> aliveGood() {
        return alivePlayers().stream().filter(p -> !p.getRole().isWerewolf()).toList();
    }

    private Player player(String id) {
        return players.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
    }

    private Player rolePlayer(Role role) {
        return alivePlayers().stream().filter(p -> p.getRole() == role).findFirst().orElse(null);
    }

    // ─────────────────────────────────────────────────────────────
    // Night flow
    // ─────────────────────────────────────────────────────────────

    private void beginNight() {
        round += 1;
        phase = Phase.NIGHT_WOLF;
        waiting = null;
        wolfTarget = null;
        witchSaveTarget = null;
        witchPoisonTarget = null;
        witchPotionUsedThisNight = false;
        seerCheckTarget = null;
        seerCheckResult = null;
        humanVoteTarget = null;
        pendingHunterId = null;
        log(null, "night", "第 " + round + " 夜，天黑请闭眼。");
        notifySnapshot();
    }

    private void doWolfPhase() {
        if (aliveWerewolves().isEmpty()) {
            finishGame("VILLAGER");
            return;
        }
        if (human().isAlive() && human().getRole() == Role.WEREWOLF) {
            List<Option> options = aliveGood().stream()
                    .map(p -> new Option(p.getId(), p.getName()))
                    .toList();
            if (options.isEmpty()) {
                checkWin();
                return;
            }
            waiting = new Waiting("WOLF_KILL", "狼人行动", "今晚要和狼队一起袭击谁？", options);
            notifySnapshot();
            return;
        }
        notifyThinking("狼人正在交换意见，判断今晚的袭击目标…");
        wolfTarget = chooseWolfTarget();
        Player victim = player(wolfTarget);
        if (victim != null) {
            log(null, "night", "狼人结束了夜间商议，悄悄靠近了某个房间。");
            if (human().getRole() == Role.WEREWOLF) {
                log(humanId, "private", "狼人今晚的目标是 " + victim.getName() + "。");
            } else if (human().getRole() == Role.WITCH && human().isAlive()) {
                log(humanId, "private", "今晚 " + victim.getName() + " 被狼人袭击了。");
            }
        }
        phase = Phase.NIGHT_WITCH_SAVE;
        notifySnapshot();
    }

    private void doWitchSave() {
        Player witch = rolePlayer(Role.WITCH);
        if (witch == null || !witch.antidote || wolfTarget == null) {
            phase = Phase.NIGHT_WITCH_POISON;
            return;
        }
        Player victim = player(wolfTarget);
        if (witch.isHuman()) {
            List<Option> options = new ArrayList<>();
            options.add(new Option(wolfTarget, "用解药救 " + victim.getName()));
            options.add(new Option("pass", "保留解药"));
            waiting = new Waiting("WITCH_SAVE", "女巫的解药",
                    "昨晚 " + victim.getName() + " 被狼人袭击。你要使用解药吗？", options);
            notifySnapshot();
            return;
        }
        notifyThinking("女巫正在判断是否使用解药…");
        boolean save = aiWitchSave(witch, victim);        if (save) {
            witchSaveTarget = wolfTarget;
            witch.antidote = false;
            witchPotionUsedThisNight = true;
            log(witch.getId(), "private", "你使用解药救下了 " + victim.getName() + "。");
            log(null, "night", "女巫似乎做出了一次关键的抉择。");
        } else {
            log(witch.getId(), "private", "你选择保留解药。");
        }
        phase = Phase.NIGHT_WITCH_POISON;
        notifySnapshot();
    }

    private void doWitchPoison() {
        Player witch = rolePlayer(Role.WITCH);
        if (witch == null || !witch.poison || witchPotionUsedThisNight) {
            phase = Phase.NIGHT_SEER;
            return;
        }
        if (witch.isHuman()) {
            List<Option> options = alivePlayers().stream()
                    .filter(p -> !p.getId().equals(witch.getId()))
                    .map(p -> new Option(p.getId(), p.getName()))
                    .collect(Collectors.toCollection(ArrayList::new));
            options.add(new Option("pass", "不使用毒药"));
            waiting = new Waiting("WITCH_POISON", "女巫的毒药",
                    "你可以毒杀一名玩家，但每晚最多使用一瓶药。要使用毒药吗？", options);
            notifySnapshot();
            return;
        }
        notifyThinking("女巫正在判断是否使用毒药…");
        String target = aiWitchPoison(witch);
        if (target != null) {
            witchPoisonTarget = target;
            witch.poison = false;
            witchPotionUsedThisNight = true;
            log(witch.getId(), "private", "你对 " + player(target).getName() + " 使用了毒药。");
            log(null, "night", "空气中飘过一丝不易察觉的苦味。");
        }
        phase = Phase.NIGHT_SEER;
        notifySnapshot();
    }

    private void doSeerCheck() {
        Player seer = rolePlayer(Role.SEER);
        if (seer == null) {
            phase = Phase.DAWN;
            return;
        }
        if (seer.isHuman()) {
            List<Option> options = alivePlayers().stream()
                    .filter(p -> !p.getId().equals(seer.getId()))
                    .map(p -> new Option(p.getId(), p.getName()))
                    .toList();
            waiting = new Waiting("SEER_CHECK", "预言家的查验",
                    "选择一名玩家查验阵营。查验结果只有你能看到。", options);
            notifySnapshot();
            return;
        }
        notifyThinking("预言家正在选择今晚的查验目标…");
        Player target = chooseSeerCheck(seer);
        if (target != null) {
            seerChecks.put(target.getId(), target.getRole());
            seerCheckTarget = target.getId();
            seerCheckResult = target.getRole();
            log(seer.getId(), "private", "查验结果：" + target.getName() + " 是 "
                    + (target.getRole().isWerewolf() ? "狼人" : "好人") + "。");
        }
        phase = Phase.DAWN;
        notifySnapshot();
    }

    private void doDawn() {
        List<Player> nightDeaths = new ArrayList<>();
        if (wolfTarget != null && !wolfTarget.equals(witchSaveTarget)) {
            Player victim = player(wolfTarget);
            if (victim != null && victim.isAlive()) {
                kill(victim, "wolf");
                nightDeaths.add(victim);
            }
        }
        if (witchPoisonTarget != null) {
            Player poisoned = player(witchPoisonTarget);
            if (poisoned != null && poisoned.isAlive()) {
                kill(poisoned, "poison");
                nightDeaths.add(poisoned);
            }
        }

        if (nightDeaths.isEmpty()) {
            log(null, "dawn", "天亮了。昨晚是平安夜。");
        } else {
            String names = nightDeaths.stream()
                    .map(p -> p.getName() + "（" + p.getRole().getLabel() + "）")
                    .collect(Collectors.joining("、"));
            log(null, "dawn", "天亮了。昨晚倒下的是：" + names + "。");
        }

        Player hunter = nightDeaths.stream()
                .filter(p -> p.getRole() == Role.HUNTER && p.canShoot && "wolf".equals(p.getDeathCause()))
                .findFirst().orElse(null);
        if (hunter != null) {
            pendingHunterId = hunter.getId();
            hunterFromVote = false;
            phase = Phase.HUNTER_SHOOT;
            log(null, "hunter", hunter.getName() + " 是猎人，倒下前举起了枪。");
            return;
        }

        if (checkWin()) return;
        phase = Phase.DISCUSSION;
        notifySnapshot();
    }

    // ─────────────────────────────────────────────────────────────
    // Day flow
    // ─────────────────────────────────────────────────────────────

    private void doDiscussion() {
        if (discussionReady) return;
        discussionReady = true;
        discussionQueue.clear();
        discussionSpoken.clear();

        Player seer = rolePlayer(Role.SEER);
        if (seer != null && !seer.isHuman() && !seerClaimed) {
            String knownWolf = seerChecks.entrySet().stream()
                    .filter(e -> e.getValue().isWerewolf())
                    .map(Map.Entry::getKey)
                    .filter(id -> player(id) != null && player(id).isAlive())
                    .findFirst().orElse(null);
            if (knownWolf != null && (round >= 2 || aliveWerewolves().size() <= 1)) {
                claimSeer(seer, knownWolf);
            }
        }

        alivePlayers().stream()
                .sorted(Comparator.comparingInt(Player::getSeat))
                .forEach(p -> discussionQueue.add(p.getId()));
        log(null, "system", "开始发言，按座位顺序轮流说话。");
        notifySnapshot();
    }

    private void doVote() {
        if (human().isAlive() && humanVoteTarget == null) {
            List<Option> options = alivePlayers().stream()
                    .filter(p -> !p.getId().equals(humanId))
                    .map(p -> new Option(p.getId(), p.getName()))
                    .toList();
            waiting = new Waiting("VOTE", "放逐投票", "选择一名玩家投票放逐。", options);
            notifySnapshot();
            return;
        }
        finalizeVote();
    }

    private void finalizeVote() {
        Map<String, String> modelVotes = Map.of();
        if (decisionProvider != null) {
            List<Player> voters = alivePlayers().stream().filter(p -> !p.isHuman()).toList();
            if (!voters.isEmpty()) {
                notifyThinking("AI 正在结合发言、查验和票型决定放逐票…");
                try {
                    Map<String, String> generated = decisionProvider.chooseVotes(this, voters);
                    if (generated != null) modelVotes = generated;
                } catch (Exception ignored) {
                    modelVotes = Map.of();
                }
            }
        }
        Map<String, String> votes = new LinkedHashMap<>();
        for (Player p : alivePlayers()) {
            if (p.isHuman()) {
                Player voteTarget = humanVoteTarget == null ? null : player(humanVoteTarget);
                if (voteTarget != null && voteTarget.isAlive()) {
                    votes.put(p.getId(), humanVoteTarget);
                }
            } else {
                String target = chooseVote(p, modelVotes);
                if (target != null) votes.put(p.getId(), target);
            }
        }
        humanVoteTarget = null;

        if (votes.isEmpty()) {
            log(null, "vote", "本轮没有人投出有效票，无人被放逐。");
            if (checkWin()) return;
            beginNight();
            return;
        }

        for (Map.Entry<String, String> e : votes.entrySet()) {
            Player voter = player(e.getKey());
            Player target = player(e.getValue());
            if (voter != null && target != null) {
                log(null, "vote", voter.getName() + " → " + target.getName());
                publicSuspicion.merge(target.getId(), 1, Integer::sum);
            }
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String target : votes.values()) counts.merge(target, 1, Integer::sum);
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> tied = counts.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .toList();
        String eliminatedId = tied.get(random.nextInt(tied.size()));
        Player eliminated = player(eliminatedId);
        if (eliminated == null) {
            beginNight();
            return;
        }

        log(null, "vote", "投票结果：" + eliminated.getName() + " 以 " + max + " 票被放逐。");
        kill(eliminated, "vote");

        if (eliminated.getRole() == Role.HUNTER && eliminated.canShoot) {
            pendingHunterId = eliminated.getId();
            hunterFromVote = true;
            phase = Phase.HUNTER_SHOOT;
            log(null, "hunter", eliminated.getName() + " 是猎人，临死前举起了枪。");
            return;
        }

        if (checkWin()) return;
        beginNight();
    }

    private void doHunter() {
        Player hunter = pendingHunterId == null ? null : player(pendingHunterId);
        if (hunter == null || !hunter.canShoot) {
            finishHunter(null);
            return;
        }
        if (hunter.isHuman()) {
            List<Option> options = alivePlayers().stream()
                    .filter(p -> !p.getId().equals(hunter.getId()))
                    .map(p -> new Option(p.getId(), p.getName()))
                    .toList();
            if (options.isEmpty()) {
                finishHunter(null);
                return;
            }
            waiting = new Waiting("HUNTER_SHOOT", "猎人开枪",
                    "你倒下了，但还能开枪带走一名玩家。选择你的目标。", options);
            notifySnapshot();
            return;
        }
        notifyThinking("猎人正在决定这一枪带走谁…");
        Player target = chooseHunterShot(hunter);
        finishHunter(target);
    }

    private void finishHunter(Player target) {
        Player hunter = pendingHunterId == null ? null : player(pendingHunterId);
        if (hunter != null) hunter.canShoot = false;
        if (target != null && target.isAlive()) {
            log(null, "hunter", hunter == null ? "猎人开枪了。" : hunter.getName() + " 开枪带走了 " + target.getName() + "。");
            kill(target, "hunter");
        } else {
            log(null, "hunter", "猎人没有开枪，带着遗憾离开了。");
        }
        pendingHunterId = null;
        if (checkWin()) return;
        if (hunterFromVote) {
            beginNight();
        } else {
            phase = Phase.DISCUSSION;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Human action API
    // ─────────────────────────────────────────────────────────────

    public void humanAction(String type, String targetId, String text) {
        if (over) throw new IllegalStateException("游戏已经结束");
        if ("SPEAK".equals(type)) {
            if (phase != Phase.DISCUSSION || !discussionQueue.contains(humanId)) {
                throw new IllegalStateException("现在还没有轮到你发言");
            }
            String speech = text == null ? "" : text.trim();
            if (speech.isEmpty()) throw new IllegalArgumentException("发言不能为空");
            if (speech.length() > 160) speech = speech.substring(0, 160);
            recordSpeech(humanId, speech);
            return;
        }
        Waiting current = waiting;
        if (current == null) throw new IllegalStateException("当前没有等待你的操作");
        if (!type.equals(current.getType())) throw new IllegalStateException("操作类型与当前阶段不匹配");
        boolean valid = current.getOptions().stream().anyMatch(o -> o.id().equals(targetId));
        if (!valid) throw new IllegalArgumentException("请选择一个有效的目标");

        waiting = null;
        switch (type) {
            case "WOLF_KILL" -> {
                Player target = player(targetId);
                if (target == null || !target.isAlive() || target.getRole().isWerewolf()) {
                    throw new IllegalArgumentException("狼人不能袭击狼队友");
                }
                wolfTarget = targetId;
                log(humanId, "private", "你和狼队决定袭击 " + target.getName() + "。");
                log(null, "night", "狼队已经做出决定。");
                phase = Phase.NIGHT_WITCH_SAVE;
            }
            case "WITCH_SAVE" -> {
                if (!"pass".equals(targetId)) {
                    Player witch = human();
                    if (witch.getRole() != Role.WITCH || !witch.antidote) {
                        throw new IllegalStateException("没有可用的解药");
                    }
                    witchSaveTarget = targetId;
                    witch.antidote = false;
                    witchPotionUsedThisNight = true;
                    log(null, "night", "女巫使用了解药。");
                    log(humanId, "private", "你救下了 " + player(targetId).getName() + "。");
                } else {
                    log(humanId, "private", "你选择保留解药。");
                }
                phase = Phase.NIGHT_WITCH_POISON;
            }
            case "WITCH_POISON" -> {
                if (!"pass".equals(targetId)) {
                    Player witch = human();
                    if (witch.getRole() != Role.WITCH || !witch.poison) {
                        throw new IllegalStateException("没有可用的毒药");
                    }
                    Player target = player(targetId);
                    if (target == null || !target.isAlive() || target.getId().equals(humanId)) {
                        throw new IllegalArgumentException("毒药目标无效");
                    }
                    witchPoisonTarget = targetId;
                    witch.poison = false;
                    witchPotionUsedThisNight = true;
                    log(null, "night", "女巫使用了毒药。");
                    log(humanId, "private", "你对 " + target.getName() + " 使用了毒药。");
                }
                phase = Phase.NIGHT_SEER;
            }
            case "SEER_CHECK" -> {
                Player target = player(targetId);
                if (target == null || !target.isAlive() || target.getId().equals(humanId)) {
                    throw new IllegalArgumentException("查验目标无效");
                }
                seerChecks.put(targetId, target.getRole());
                seerCheckTarget = targetId;
                seerCheckResult = target.getRole();
                log(humanId, "private", "查验结果：" + target.getName() + " 是 "
                        + (target.getRole().isWerewolf() ? "狼人" : "好人") + "。");
                phase = Phase.DAWN;
            }
            case "VOTE" -> {
                humanVoteTarget = targetId;
                phase = Phase.VOTE;
            }
            case "HUNTER_SHOOT" -> {
                Player target = player(targetId);
                if (target == null || !target.isAlive() || target.getId().equals(humanId)) {
                    throw new IllegalArgumentException("开枪目标无效");
                }
                finishHunter(target);
                return;
            }
            default -> throw new IllegalStateException("未知操作类型");
        }
        advance();
    }

    public void autoPlay() {
        int guard = 0;
        while (!over && guard++ < 400) {
            if (phase == Phase.DISCUSSION) {
                autoDiscussRemaining();
                continue;
            }
            if (waiting != null) {
                autoResolveWaiting();
                continue;
            }
            advance();
        }
    }

    private void autoResolveWaitingInternal() {
        Waiting current = waiting;
        if (current == null) return;
        String type = current.getType();
        String target = switch (type) {
            case "WOLF_KILL" -> chooseWolfTarget();
            case "WITCH_SAVE" -> {
                Player victim = wolfTarget == null ? null : player(wolfTarget);
                if (victim != null && aiWitchSave(human(), victim)) yield wolfTarget;
                yield "pass";
            }
            case "WITCH_POISON" -> aiWitchPoison(human());
            case "SEER_CHECK" -> {
                Player targetPlayer = chooseSeerCheck(human());
                yield targetPlayer == null ? "pass" : targetPlayer.getId();
            }
            case "VOTE" -> autoHumanVote();
            case "HUNTER_SHOOT" -> {
                Player targetPlayer = chooseHunterShot(human());
                yield targetPlayer == null ? "pass" : targetPlayer.getId();
            }
            default -> "pass";
        };
        if (target == null) target = "pass";
        try {
            humanAction(type, target, null);
        } catch (RuntimeException ex) {
            // A malformed automatic choice should never freeze an auto game.
            waiting = null;
            advance();
        }
    }

    private String autoHumanVote() {
        if (decisionProvider != null) {
            try {
                Map<String, String> generated = decisionProvider.chooseVotes(this, List.of(human()));
                String target = generated == null ? null : generated.get(humanId);
                Player targetPlayer = target == null ? null : player(target);
                if (targetPlayer != null && targetPlayer.isAlive() && !targetPlayer.getId().equals(humanId)) {
                    return target;
                }
            } catch (Exception ignored) {
                // fall through to local safety choice
            }
        }
        return chooseVote(human(), null);
    }

    public void advance() {
        int guard = 0;
        while (!over && waiting == null && guard++ < 60) {
            if (phase == Phase.DISCUSSION) {
                doDiscussion();
                return;
            }
            switch (phase) {
                case NIGHT_WOLF -> doWolfPhase();
                case NIGHT_WITCH_SAVE -> doWitchSave();
                case NIGHT_WITCH_POISON -> doWitchPoison();
                case NIGHT_SEER -> doSeerCheck();
                case DAWN -> doDawn();
                case DISCUSSION -> { doDiscussion(); return; }
                case VOTE -> doVote();
                case HUNTER_SHOOT -> doHunter();
                case GAME_OVER -> { return; }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Agent decisions
    // ─────────────────────────────────────────────────────────────

    private String chooseWolfTarget() {
        if (decisionProvider != null) {
            try {
                String planned = decisionProvider.chooseWolfTarget(this);
                Player target = planned == null ? null : player(planned);
                return target != null && target.isAlive() && !target.getRole().isWerewolf() ? target.getId() : null;
            } catch (Exception ignored) {
                // model unavailable: safe engine fallback
            }
        }
        return ruleWolfTarget();
    }

    private String ruleWolfTarget() {
        List<Player> candidates = aliveGood();
        if (candidates.isEmpty()) return null;
        Player best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Player p : candidates) {
            int score = random.nextInt(4) + publicSuspicion.getOrDefault(p.getId(), 0);
            if (p.getId().equals(claimedSeerId)) score += 14;
            if ("预言家".equals(p.getClaimedRole())) score += 12;
            if (p.getRole() == Role.SEER) score += 2;
            if (score > bestScore) { bestScore = score; best = p; }
        }
        return best == null ? null : best.getId();
    }

    private boolean aiWitchSave(Player witch, Player victim) {
        if (witch == null || victim == null || !witch.antidote) return false;
        if (decisionProvider != null) {
            try {
                Boolean save = decisionProvider.chooseWitchSave(this, witch, victim);
                return save != null && save;
            } catch (Exception ignored) {
                // model unavailable: safe engine fallback
            }
        }
        return ruleWitchSave(victim);
    }

    private boolean ruleWitchSave(Player victim) {
        if (round == 1) return true;
        if (victim.getId().equals(claimedSeerId)) return true;
        return random.nextDouble() < 0.35;
    }

    private String aiWitchPoison(Player witch) {
        if (witch == null || !witch.poison) return null;
        if (decisionProvider != null) {
            try {
                String planned = decisionProvider.chooseWitchPoison(this, witch);
                if (planned == null || planned.isBlank()) return null;
                Player target = player(planned);
                return target != null && target.isAlive() && !target.getId().equals(witch.getId())
                        ? target.getId() : null;
            } catch (Exception ignored) {
                // model unavailable: safe engine fallback
            }
        }
        return ruleWitchPoison(witch);
    }

    private String ruleWitchPoison(Player witch) {
        if (round < 2) return null;
        String knownWolf = seerChecks.entrySet().stream()
                .filter(e -> e.getValue().isWerewolf())
                .map(Map.Entry::getKey)
                .filter(id -> player(id) != null && player(id).isAlive())
                .findFirst().orElse(null);
        if (knownWolf != null && random.nextDouble() < 0.75) return knownWolf;
        List<Player> suspects = alivePlayers().stream()
                .filter(p -> !p.getId().equals(witch.getId()))
                .sorted(Comparator.comparingInt((Player p) -> publicSuspicion.getOrDefault(p.getId(), 0)).reversed())
                .toList();
        if (!suspects.isEmpty() && random.nextDouble() < 0.32) return suspects.get(0).getId();
        return null;
    }

    private Player chooseSeerCheck(Player seer) {
        if (decisionProvider != null) {
            try {
                String planned = decisionProvider.chooseSeerCheck(this, seer);
                Player target = planned == null ? null : player(planned);
                return target != null && target.isAlive() && !target.getId().equals(seer.getId()) ? target : null;
            } catch (Exception ignored) {
                // model unavailable: safe engine fallback
            }
        }
        return ruleSeerCheck(seer);
    }

    private Player ruleSeerCheck(Player seer) {
        List<Player> candidates = alivePlayers().stream()
                .filter(p -> !p.getId().equals(seer.getId()))
                .sorted(Comparator.comparingInt((Player p) -> publicSuspicion.getOrDefault(p.getId(), 0)).reversed())
                .toList();
        for (Player p : candidates) {
            if (!seerChecks.containsKey(p.getId())) return p;
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private String chooseVote(Player voter, Map<String, String> modelVotes) {
        if (modelVotes != null) {
            String planned = modelVotes.get(voter.getId());
            if (planned != null) {
                Player target = player(planned);
                if (target != null && target.isAlive() && !target.getId().equals(voter.getId())) {
                    return planned;
                }
            }
        }
        return ruleVote(voter);
    }

    private String ruleVote(Player voter) {
        List<Player> candidates = alivePlayers().stream()
                .filter(p -> !p.getId().equals(voter.getId()))
                .toList();
        if (candidates.isEmpty()) return null;
        if (voter.getRole() == Role.SEER) {
            for (Map.Entry<String, Role> e : seerChecks.entrySet()) {
                if (e.getValue().isWerewolf()) {
                    Player wolf = player(e.getKey());
                    if (wolf != null && wolf.isAlive()) return wolf.getId();
                }
            }
        }
        List<Player> pool = candidates;
        if (voter.getRole().isWerewolf()) {
            pool = candidates.stream().filter(p -> !p.getRole().isWerewolf()).toList();
        }
        if (pool.isEmpty()) pool = candidates;
        Player best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Player p : pool) {
            int score = publicSuspicion.getOrDefault(p.getId(), 0) + random.nextInt(5);
            if (publicWolfIds.contains(p.getId())) score += 12;
            if (p.getId().equals(claimedSeerId) && voter.getRole().isWerewolf()) score += 10;
            if (p.getRole().isWerewolf() && voter.getRole().isWerewolf()) score -= 16;
            if (score > bestScore) { bestScore = score; best = p; }
        }
        return best == null ? null : best.getId();
    }

    private Player chooseHunterShot(Player hunter) {
        if (decisionProvider != null) {
            try {
                String planned = decisionProvider.chooseHunterShot(this, hunter);
                Player target = planned == null ? null : player(planned);
                return target != null && target.isAlive() && !target.getId().equals(hunter.getId()) ? target : null;
            } catch (Exception ignored) {
                // model unavailable: safe engine fallback
            }
        }
        return ruleHunterShot(hunter);
    }

    private Player ruleHunterShot(Player hunter) {
        List<Player> candidates = alivePlayers().stream()
                .filter(p -> !p.getId().equals(hunter.getId()))
                .sorted(Comparator.comparingInt((Player p) -> publicSuspicion.getOrDefault(p.getId(), 0)).reversed())
                .toList();
        if (candidates.isEmpty()) return null;
        for (Player p : candidates) {
            if (publicWolfIds.contains(p.getId())) return p;
        }
        return candidates.get(0);
    }

    private void claimSeer(Player seer, String wolfId) {
        seerClaimed = true;
        claimedSeerId = seer.getId();
        Player wolf = player(wolfId);
        if (wolf != null) {
            wolf.claimedRole = "狼人";
            publicWolfIds.add(wolfId);
            publicSuspicion.merge(wolfId, 15, Integer::sum);
        }
        log(null, "claim", seer.getName() + " 站了出来：“我是预言家，我查验了 "
                + (wolf == null ? "一名玩家" : wolf.getName()) + "，他是狼人。”");
        log(seer.getId(), "private", "你公开了预言家身份，并指认了 " + (wolf == null ? "一名玩家" : wolf.getName()) + "。");
    }

    private String generateSpeech(Player p) {
        String opener = SPEECH_OPENERS[random.nextInt(SPEECH_OPENERS.length)];
        if (p.getId().equals(claimedSeerId)) {
            String wolfId = seerChecks.entrySet().stream()
                    .filter(e -> e.getValue().isWerewolf())
                    .map(Map.Entry::getKey)
                    .filter(id -> player(id) != null && player(id).isAlive())
                    .findFirst().orElse(null);
            if (wolfId != null) {
                return "我是预言家，查验 " + player(wolfId).getName() + " 是狼人。今天请把票投给他。";
            }
            return "我是预言家。好人牌请跟我的查验走，不要被狼人带偏。";
        }

        List<Player> suspects = alivePlayers().stream()
                .filter(x -> !x.getId().equals(p.getId()))
                .sorted(Comparator.comparingInt((Player x) -> publicSuspicion.getOrDefault(x.getId(), 0)).reversed())
                .toList();
        Player suspect = suspects.isEmpty() ? null : suspects.get(0);

        if (p.getRole().isWerewolf()) {
            if (suspect != null) {
                return opener + "我觉得 " + suspect.getName() + " 的发言有点飘，先把票压在他身上试试。";
            }
            return opener + "场上信息还少，我不想被情绪带着走。";
        }
        if (p.getRole() == Role.WITCH) {
            return opener + "我暂时不站边，但 " + (suspect == null ? "有人" : suspect.getName())
                    + " 的节奏让我不太舒服，建议大家多听一轮。";
        }
        if (p.getRole() == Role.HUNTER) {
            return opener + "我建议先把最可疑的人推出去。如果推错，后面还有机会修正。";
        }
        if (suspect != null) {
            return opener + "我对 " + suspect.getName() + " 有点疑问，他的投票和发言对不上。";
        }
        return opener + "我先保留判断，等下一轮信息。";
    }

    // ─────────────────────────────────────────────────────────────
    // Results and view
    // ─────────────────────────────────────────────────────────────

    private void kill(Player player, String cause) {
        if (player == null || !player.isAlive()) return;
        player.alive = false;
        player.deathCause = cause;
        player.revealed = true;
        publicSuspicion.remove(player.getId());
        log(null, "death", player.getName() + " 出局，身份是【" + player.getRole().getLabel() + "】。");
    }

    private boolean checkWin() {
        long wolves = aliveWerewolves().size();
        long good = aliveGood().size();
        if (wolves == 0) {
            finishGame("VILLAGER");
            return true;
        }
        if (wolves >= good) {
            finishGame("WEREWOLF");
            return true;
        }
        return false;
    }

    private void finishGame(String winnerCode) {
        if (over) return;
        over = true;
        winner = winnerCode;
        phase = Phase.GAME_OVER;
        waiting = null;
        String label = "WEREWOLF".equals(winnerCode) ? "狼人阵营" : "好人阵营";
        log(null, "result", "游戏结束！" + label + "获胜。");
    }

    private void log(String privateTo, String type, String text) {
        logSeq += 1;
        LogEntry entry = new LogEntry(logSeq, round, phase.name(), phase.getLabel(), type, text, privateTo);
        logs.add(entry);
        if (logs.size() > 260) logs.remove(0);
        if (eventListener != null) {
            try {
                eventListener.onLog(entry);
                eventListener.onSnapshot(toView());
            } catch (Exception ignored) {
                // The SSE client may have closed. Game state remains valid.
            }
        }
    }

    private PlayerView toPlayerView(Player p) {
        boolean visible = p.isHuman() || p.revealed || !p.isAlive();
        String role = visible ? p.getRole().name() : null;
        String roleLabel = visible ? p.getRole().getLabel() : null;
        String roleIcon = visible ? p.getRole().getIcon() : null;
        String team = visible ? p.getRole().getTeam().name() : null;
        String claimed = p.getClaimedRole();
        return new PlayerView(
                p.getId(), p.getSeat(), p.getName(), p.isAlive(), p.isHuman(),
                role, roleLabel, roleIcon, team, p.getDeathCause(),
                claimed, claimed, publicSuspicion.getOrDefault(p.getId(), 0)
        );
    }

    public GameView toView() {
        Player me = human();
        List<PlayerView> playerViews = players.stream()
                .sorted(Comparator.comparingInt(Player::getSeat))
                .map(this::toPlayerView)
                .toList();
        List<LogView> logViews = logs.stream()
                .filter(l -> l.privateTo() == null || l.privateTo().equals(humanId))
                .map(l -> new LogView(l.seq(), l.round(), l.phase(), l.phaseLabel(), l.type(), l.text(),
                        l.privateTo() != null))
                .toList();
        List<String> teammates = me.getRole() == Role.WEREWOLF
                ? players.stream()
                    .filter(p -> p.getRole() == Role.WEREWOLF && !p.getId().equals(humanId))
                    .map(p -> p.getName() + (p.isAlive() ? "" : "（已出局）"))
                    .toList()
                : List.of();
        AbilitiesView abilities = new AbilitiesView(me.antidote, me.poison, me.canShoot);
        WaitingView waitingView = waiting == null ? null
                : new WaitingView(waiting.getType(), waiting.getTitle(), waiting.getPrompt(), waiting.getOptions());
        String summary = "存活 " + alivePlayers().size() + " / " + players.size()
                + " · 狼人 " + aliveWerewolves().size() + " · 好人 " + aliveGood().size();
        return new GameView(
                id, round, phase.name(), phase.getLabel(), phase.getHint(),
                over, winner, winner == null ? null : ("WEREWOLF".equals(winner) ? "狼人阵营" : "好人阵营"),
                humanId, me.isAlive(), me.getRole().name(), me.getRole().getLabel(), me.getRole().getIcon(),
                me.getRole().getTeam().getLabel(), me.getRole().getDescription(), teammates, abilities,
                waitingView, playerViews, logViews, summary
        );
    }

    public String getId() { return id; }
    public boolean isOver() { return over; }
}
