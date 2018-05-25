package com.mindata.blockchain.socket.pbft.queue;

import cn.hutool.core.collection.CollectionUtil;
import com.mindata.blockchain.socket.pbft.msg.VoteMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wuweifeng wrote on 2018/4/26.
 */
public abstract class AbstractVoteMsgQueue extends BaseMsgQueue {
    /**
     * 存储所有的hash的投票集合
     */
    protected ConcurrentHashMap<String, List<VoteMsg>> voteMsgConcurrentHashMap = new ConcurrentHashMap<>();
    /**
     * 存储本节点已确认状态的hash的集合，即本节点已对外广播过允许commit或拒绝commit的消息了
     */
    protected ConcurrentHashMap<String, Boolean> voteStateConcurrentHashMap = new ConcurrentHashMap<>();

    private Logger logger = LoggerFactory.getLogger(getClass());

    abstract void deal(VoteMsg voteMsg, List<VoteMsg> voteMsgs);

    @Override
    protected void push(VoteMsg voteMsg) {
        String hash = voteMsg.getHash();
        List<VoteMsg> voteMsgs = voteMsgConcurrentHashMap.get(hash);
        if (CollectionUtil.isEmpty(voteMsgs)) {
            voteMsgs = new ArrayList<>();
            voteMsgConcurrentHashMap.put(hash, voteMsgs);
        } else {
            //如果不空的情况下，判断本地集合是否已经存在完全相同的voteMsg了
            for (VoteMsg temp : voteMsgs) {
                if (temp.getAppId().equals(voteMsg.getAppId())) {
                    return;
                }
            }
        }

        //添加进去
        voteMsgs.add(voteMsg);
        //如果我已经对该hash的commit投过票了，就不再继续
        if (voteStateConcurrentHashMap.get(hash) != null) {
            return;
        }

        deal(voteMsg, voteMsgs);
    }

    /**
     * 该方法用来确认待push阶段的下一阶段是否已存在已达成共识的Block <p>
     * 譬如收到了区块5的Prepare的投票信息，那么我是否接受该投票，需要先校验Commit阶段是否已经存在number>=5的投票成功信息
     *
     * @param hash
     *         hash
     * @return 是否超过
     */
    public boolean hasOtherConfirm(String hash, int number) {
        //遍历该阶段的所有投票信息
        for (String key : voteMsgConcurrentHashMap.keySet()) {
            //如果下一阶段存在同一个hash的投票，则不理会
            if (hash.equals(key)) {
                continue;
            }
            //如果下一阶段的number比当前投票的小，则不理会
            if (voteMsgConcurrentHashMap.get(key).get(0).getNumber() < number) {
                continue;
            }
            //只有别的>=number的Block已经达成共识了，则返回true，那么将会拒绝该hash进入下一阶段
            if (voteStateConcurrentHashMap.get(key) != null && voteStateConcurrentHashMap.get(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清理旧的block的hash
     */
    protected void clearOldBlockHash(int number) {
        CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (String key : voteMsgConcurrentHashMap.keySet()) {
                if (voteMsgConcurrentHashMap.get(key).get(0).getNumber() <= number) {
                    voteMsgConcurrentHashMap.remove(key);
                    voteStateConcurrentHashMap.remove(key);
                }
            }
            return null;
        });
    }
}
