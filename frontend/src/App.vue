<template>
  <!--
    氛围背景：刻意放在过渡之外。
    原因：祖先元素上的 transform 会把 position:fixed 的锚点从视口改为该元素，
    背景会随过渡"滑动"，是切换时视觉拉伸的主要原因。
  -->
  <div class="app-ambient" aria-hidden="true" />

  <!--
    栏目切换过渡（out-in：旧页先退场，新页再入场，避免两页重叠）。
    关键设计：
    - 离场只做透明度：不加 transform，旧页中的 sticky 导航与 fixed 元素不会被影响
    - 入场做透明度 + 6px 上浮：新页已回到顶部，位移不会引起可见的布局跳动
    - 全程不用 scale / filter: blur：避免全页缩放"拉伸感"与模糊造成的高开销重绘
  -->
  <router-view v-slot="{ Component }">
    <transition name="page" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<script setup lang="ts"></script>

<!-- 非 scoped：过渡类会被添加到路由组件根元素上 -->
<style>
/* 氛围背景：常驻不参与过渡，切换栏目时保持完全静止 */
.app-ambient {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  background:
    radial-gradient(560px 320px at 0% 0%, rgba(124, 58, 237, 0.07), transparent 65%),
    radial-gradient(520px 300px at 100% 8%, rgba(5, 150, 105, 0.06), transparent 65%);
}

/*
  过渡策略：整页只做透明度，位移只施加在"内容区"。
  原因：
  - 整页 transform 会让 sticky 导航与 fixed 元素重新锚定，并打断 backdrop-filter 的毛玻璃取样
  - 只对内容区加位移，导航栏保持静止，既有"上浮"质感又不会有任何抖动
  分工：根元素负责淡入淡出，内容区只负责位移（避免双重透明度相乘导致曲线失真）
*/
.page-enter-active {
  transition: opacity 160ms cubic-bezier(0.16, 1, 0.3, 1);
}
.page-leave-active {
  transition: opacity 100ms cubic-bezier(0.4, 0, 1, 1);
}
.page-enter-from,
.page-leave-to {
  opacity: 0;
}

/* 内容区上浮：AppLayout 用 .main-content，登录页用 .login-card */
.page-enter-active :is(.main-content, .login-card) {
  transition: transform 220ms cubic-bezier(0.16, 1, 0.3, 1);
}
.page-enter-from :is(.main-content, .login-card) {
  transform: translateY(10px);
}

/* 降级：偏好减少动效时只保留极短的透明度切换 */
@media (prefers-reduced-motion: reduce) {
  .page-enter-active,
  .page-leave-active {
    transition: opacity 90ms linear;
  }
  .page-enter-active :is(.main-content, .login-card) {
    transition: none;
  }
  .page-enter-from :is(.main-content, .login-card) {
    transform: none;
  }
}
</style>
