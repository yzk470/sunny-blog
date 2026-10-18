<template>
  <div class="sunny-decor" aria-hidden="true">
    <div class="decor-sun">
      <span class="sun-eye left"></span>
      <span class="sun-eye right"></span>
      <span class="sun-smile"></span>
    </div>
    <div class="decor-cloud cloud-one"></div>
    <div class="decor-cloud cloud-two"></div>
    <div class="decor-cloud cloud-three"></div>
    <span class="decor-sparkle sparkle-one">✦</span>
    <span class="decor-sparkle sparkle-two">✦</span>
    <span class="decor-sparkle sparkle-three">✦</span>
    <span class="decor-dot dot-one"></span>
    <span class="decor-dot dot-two"></span>
    <span class="decor-dot dot-three"></span>
  </div>
</template>

<style scoped>
.sunny-decor {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.decor-sun {
  position: absolute;
  top: 130px;
  right: 5.5%;
  width: 210px;
  height: 210px;
  border-radius: 50%;
  background: radial-gradient(circle at 34% 30%, #fff4c8, #ffe08a 56%, #ffd178 100%);
  opacity: 0.48;
  filter: blur(0.2px);
  box-shadow: 0 0 80px rgba(255, 210, 120, 0.5);
  animation: float-y 13s ease-in-out infinite;
}

.decor-sun::before {
  content: "";
  position: absolute;
  inset: -32px;
  border-radius: 50%;
  border: 2px dashed rgba(246, 201, 107, 0.42);
  animation: spin-slow 32s linear infinite;
}

.sun-eye {
  position: absolute;
  top: 88px;
  width: 7px;
  height: 9px;
  border-radius: 50%;
  background: rgba(146, 110, 55, 0.45);
}
.sun-eye.left {
  left: 74px;
}
.sun-eye.right {
  right: 74px;
}
.sun-smile {
  position: absolute;
  left: 50%;
  top: 106px;
  width: 28px;
  height: 14px;
  transform: translateX(-50%);
  border-bottom: 3px solid rgba(146, 110, 55, 0.4);
  border-radius: 0 0 30px 30px;
}

.decor-cloud {
  position: absolute;
  width: 170px;
  height: 46px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.55);
  filter: blur(0.4px);
  animation: drift 16s ease-in-out infinite;
}

.decor-cloud::before,
.decor-cloud::after {
  content: "";
  position: absolute;
  border-radius: 50%;
  background: inherit;
}
.decor-cloud::before {
  width: 74px;
  height: 74px;
  left: 26px;
  bottom: 8px;
}
.decor-cloud::after {
  width: 52px;
  height: 52px;
  right: 28px;
  bottom: 4px;
}

.cloud-one {
  top: 265px;
  left: -32px;
  opacity: 0.72;
}
.cloud-two {
  top: 520px;
  right: 3%;
  width: 130px;
  opacity: 0.5;
  animation-delay: -6s;
}
.cloud-three {
  top: 760px;
  left: 6%;
  width: 110px;
  opacity: 0.4;
  animation-delay: -11s;
}

.decor-sparkle {
  position: absolute;
  color: rgba(246, 201, 107, 0.6);
  font-size: 1.2rem;
  animation: float-soft 5.5s ease-in-out infinite;
}
.sparkle-one {
  top: 190px;
  right: 23%;
}
.sparkle-two {
  top: 470px;
  left: 8%;
  font-size: 0.8rem;
  color: rgba(238, 155, 182, 0.55);
  animation-delay: -2s;
}
.sparkle-three {
  top: 680px;
  right: 12%;
  font-size: 0.95rem;
  animation-delay: -3.6s;
}

.decor-dot {
  position: absolute;
  border-radius: 50%;
  background: rgba(249, 198, 213, 0.5);
}
.dot-one {
  top: 340px;
  right: 20%;
  width: 8px;
  height: 8px;
}
.dot-two {
  top: 600px;
  left: 18%;
  width: 6px;
  height: 6px;
  background: rgba(169, 216, 245, 0.65);
}
.dot-three {
  top: 90px;
  left: 30%;
  width: 5px;
  height: 5px;
  background: rgba(255, 227, 163, 0.85);
}

@media (max-width: 1100px) {
  .decor-sun {
    width: 160px;
    height: 160px;
    top: 150px;
  }
  .sun-eye {
    top: 66px;
  }
  .sun-eye.left {
    left: 56px;
  }
  .sun-eye.right {
    right: 56px;
  }
  .sun-smile {
    top: 82px;
  }
  .cloud-two {
    display: none;
  }
}

@media (max-width: 700px) {
  .decor-sun {
    width: 120px;
    height: 120px;
    top: auto;
    bottom: 8%;
    right: -34px;
    opacity: 0.35;
  }
  .sun-eye,
  .sun-smile {
    display: none;
  }
  .decor-cloud {
    opacity: 0.35;
  }
  .cloud-one {
    top: 120px;
  }
  .sparkle-two,
  .sparkle-three {
    display: none;
  }
}
</style>
