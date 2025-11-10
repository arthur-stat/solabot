package com.arth.solabot.core.infrastructure.network.model;

import lombok.Getter;
import lombok.Setter;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * GIF 数据结构
 * 包含帧列表、延时信息和循环次数
 */
@Setter
@Getter
public class GifData {

    public List<BufferedImage> frames = new ArrayList<>();

    public List<Integer> delaysCs = new ArrayList<>();

    public int loopCount = 0;
}