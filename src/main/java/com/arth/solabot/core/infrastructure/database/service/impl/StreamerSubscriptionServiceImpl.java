package com.arth.solabot.core.infrastructure.database.service.impl;

import com.arth.solabot.core.infrastructure.database.domain.StreamerSubscription;
import com.arth.solabot.core.infrastructure.database.mapper.StreamerSubscriptionMapper;
import com.arth.solabot.core.infrastructure.database.service.StreamerSubscriptionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
* @author asheo
* @description 针对表【t_streamer_subscription】的数据库操作Service实现
* @createDate 2025-11-10 03:21:48
*/
@Service
public class StreamerSubscriptionServiceImpl extends ServiceImpl<StreamerSubscriptionMapper, StreamerSubscription>
    implements StreamerSubscriptionService{

}




