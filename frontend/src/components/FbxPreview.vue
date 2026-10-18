<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as THREE from 'three'
import { FBXLoader } from 'three/addons/loaders/FBXLoader.js'

const props = defineProps<{ url: string }>()
const emit = defineEmits<{ close: [] }>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const loading = ref(true)
const progress = ref(0)
const error = ref('')
const clipName = ref('')

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let mixer: THREE.AnimationMixer | null = null
let root: THREE.Object3D | null = null
let frame = 0
let resizeObserver: ResizeObserver | null = null
let disposed = false

function disposeObject(object: THREE.Object3D) {
  object.traverse((child) => {
    if (!(child instanceof THREE.Mesh)) return
    child.geometry?.dispose()
    const materials = Array.isArray(child.material) ? child.material : [child.material]
    materials.forEach((material) => {
      if (!material) return
      Object.values(material).forEach((value) => {
        if (value instanceof THREE.Texture) value.dispose()
      })
      material.dispose()
    })
  })
}

function resize() {
  const canvas = canvasRef.value
  if (!canvas || !renderer || !camera) return
  const width = canvas.clientWidth
  const height = canvas.clientHeight
  if (!width || !height) return
  renderer.setSize(width, height, false)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
}

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return

  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setClearColor('#0d1524')
  renderer.outputColorSpace = THREE.SRGBColorSpace

  scene = new THREE.Scene()
  scene.background = new THREE.Color('#0d1524')
  scene.fog = new THREE.Fog('#0d1524', 8, 18)

  camera = new THREE.PerspectiveCamera(40, 1, 0.1, 60)
  camera.position.set(0, 1.35, 4.2)
  camera.lookAt(0, 1.05, 0)

  scene.add(new THREE.HemisphereLight('#e8f0ff', '#33405c', 1.2))
  const key = new THREE.DirectionalLight('#fff4e8', 2.2)
  key.position.set(2, 4, 3)
  scene.add(key)
  const fill = new THREE.DirectionalLight('#b8dcff', 1.0)
  fill.position.set(-3, 2, -1)
  scene.add(fill)

  const grid = new THREE.GridHelper(12, 24, '#33506b', '#1b2b40')
  grid.position.y = 0
  scene.add(grid)

  resize()
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(canvas)
  window.addEventListener('resize', resize)

  const loader = new FBXLoader()
  loader.load(
    props.url,
    (object) => {
      if (disposed || !scene) return
      root = object
      object.updateMatrixWorld(true)

      // Skinned meshes often report a bind-pose box that differs from the
      // actual skeleton. Combine mesh and bone bounds so Hunyuan FBX files
      // with a translated root still get framed correctly.
      const box = new THREE.Box3().setFromObject(object)
      const boneBox = new THREE.Box3()
      const point = new THREE.Vector3()
      object.traverse((child) => {
        if (child instanceof THREE.Bone) {
          child.getWorldPosition(point)
          boneBox.expandByPoint(point)
        }
      })
      if (!boneBox.isEmpty()) box.union(boneBox)

      const size = new THREE.Vector3()
      box.getSize(size)
      const height = Math.max(size.y, 0.01)
      const scale = 1.7 / height
      object.scale.setScalar(scale)
      object.updateMatrixWorld(true)

      const scaledBox = new THREE.Box3().setFromObject(object)
      const scaledBoneBox = new THREE.Box3()
      object.traverse((child) => {
        if (child instanceof THREE.Bone) {
          child.getWorldPosition(point)
          scaledBoneBox.expandByPoint(point)
        }
      })
      if (!scaledBoneBox.isEmpty()) scaledBox.union(scaledBoneBox)

      const center = new THREE.Vector3()
      scaledBox.getCenter(center)
      object.position.x -= center.x
      object.position.z -= center.z
      object.position.y -= scaledBox.min.y
      scene.add(object)
      object.updateMatrixWorld(true)

      // Camera must fit the object *after* it has been centered above the grid.
      const finalBox = new THREE.Box3().setFromObject(object)
      const finalBoneBox = new THREE.Box3()
      object.traverse((child) => {
        if (child instanceof THREE.Bone) {
          child.getWorldPosition(point)
          finalBoneBox.expandByPoint(point)
        }
      })
      if (!finalBoneBox.isEmpty()) finalBox.union(finalBoneBox)

      const sphere = finalBox.getBoundingSphere(new THREE.Sphere())
      if (camera) {
        const fov = (camera.fov * Math.PI) / 180
        const distance = Math.max(3.2, (sphere.radius / Math.sin(fov / 2)) * 1.55)
        camera.position.set(sphere.center.x, sphere.center.y + sphere.radius * 0.14, distance)
        camera.lookAt(sphere.center.x, sphere.center.y, sphere.center.z)
      }
      console.log(
        '[FbxPreview] bounds',
        `mesh=${size.x.toFixed(2)}x${size.y.toFixed(2)}x${size.z.toFixed(2)}`,
        `radius=${sphere.radius.toFixed(2)}`,
        `scale=${scale.toFixed(3)}`,
        `min=${finalBox.min.toArray().map((v) => v.toFixed(2)).join(',')}`,
        `max=${finalBox.max.toArray().map((v) => v.toFixed(2)).join(',')}`,
        `pos=${object.position.toArray().map((v) => v.toFixed(2)).join(',')}`,
        `cam=${camera?.position.toArray().map((v) => v.toFixed(2)).join(',')}`,
        `target=${sphere.center.toArray().map((v) => v.toFixed(2)).join(',')}`,
      )

      object.traverse((child) => {
        if (child instanceof THREE.Mesh) {
          child.castShadow = true
          child.receiveShadow = true
        }
      })

      if (object.animations.length) {
        mixer = new THREE.AnimationMixer(object)
        const clip = object.animations[0]
        mixer.clipAction(clip).setLoop(THREE.LoopRepeat, Infinity).play()
        clipName.value = clip.name || 'motion'
      }
      loading.value = false
    },
    (event) => {
      if (event.lengthComputable) {
        progress.value = Math.round((event.loaded / event.total) * 100)
      }
    },
    (err) => {
      loading.value = false
      error.value = err instanceof Error ? err.message : 'FBX 加载失败'
    },
  )

  const clock = new THREE.Clock()
  const animate = () => {
    frame = requestAnimationFrame(animate)
    if (!renderer || !scene || !camera) return
    const delta = Math.min(clock.getDelta(), 0.1)
    if (mixer) mixer.update(delta)
    else if (root) root.rotation.y += delta * 0.18
    renderer.render(scene, camera)
  }
  animate()
})

onBeforeUnmount(() => {
  disposed = true
  cancelAnimationFrame(frame)
  resizeObserver?.disconnect()
  window.removeEventListener('resize', resize)
  if (root) {
    disposeObject(root)
    root = null
  }
  mixer = null
  renderer?.dispose()
  renderer = null
  scene = null
  camera = null
})
</script>

<template>
  <div class="fbx-overlay" @click.self="emit('close')">
    <section class="fbx-dialog" aria-label="混元 FBX 动作预览">
      <header class="fbx-head">
        <div>
          <p>HUNYUAN FBX PREVIEW</p>
          <h3>混元动作预览</h3>
        </div>
        <button type="button" class="fbx-close" @click="emit('close')">×</button>
      </header>
      <div class="fbx-stage">
        <canvas ref="canvasRef" class="fbx-canvas"></canvas>
        <div v-if="loading" class="fbx-loading">
          <span class="fbx-spinner"></span>
          <p>正在加载 FBX 与动画…</p>
          <small v-if="progress">{{ progress }}%</small>
        </div>
        <div v-else-if="error" class="fbx-error">
          <p>{{ error }}</p>
        </div>
        <div v-else class="fbx-badge">
          <span>{{ clipName || '静态模型' }}</span>
          <small>混元原始 FBX · 尚未套用到 PMX</small>
        </div>
      </div>
      <footer class="fbx-foot">
        <span>用于预览混元生成结果，不会替换爻光 / 镜流的 PMX 模型。</span>
        <a :href="url" target="_blank" rel="noopener noreferrer">下载 FBX</a>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.fbx-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(8, 14, 24, 0.72);
  backdrop-filter: blur(12px);
}

.fbx-dialog {
  width: min(860px, 96vw);
  overflow: hidden;
  border-radius: 26px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: #0d1524;
  box-shadow: 0 40px 100px rgba(0, 0, 0, 0.45);
  color: #edf5fb;
}

.fbx-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px 14px;
}

.fbx-head p {
  color: rgba(255, 227, 163, 0.6);
  font-size: 0.52rem;
  font-weight: 900;
  letter-spacing: 0.18em;
}

.fbx-head h3 {
  margin-top: 3px;
  color: #fff;
  font-size: 1rem;
}

.fbx-close {
  border: 0;
  background: transparent;
  color: rgba(237, 245, 251, 0.6);
  font-size: 1.4rem;
  line-height: 1;
}

.fbx-stage {
  position: relative;
  height: min(480px, 56vh);
  background: #0d1524;
}

.fbx-canvas {
  width: 100%;
  height: 100%;
  display: block;
}

.fbx-loading,
.fbx-error {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: rgba(237, 245, 251, 0.75);
  font-size: 0.75rem;
}

.fbx-spinner {
  width: 34px;
  height: 34px;
  border: 2px solid rgba(169, 216, 245, 0.18);
  border-top-color: #a9d8f5;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

.fbx-loading small {
  color: rgba(237, 245, 251, 0.35);
}

.fbx-error {
  color: #f3b8c8;
}

.fbx-badge {
  position: absolute;
  left: 16px;
  bottom: 14px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 7px 10px;
  border-radius: 10px;
  background: rgba(8, 14, 24, 0.66);
  border: 1px solid rgba(255, 255, 255, 0.12);
  font-size: 0.6rem;
}

.fbx-badge small {
  color: rgba(237, 245, 251, 0.36);
  font-size: 0.5rem;
}

.fbx-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 20px;
  color: rgba(237, 245, 251, 0.42);
  font-size: 0.56rem;
}

.fbx-foot a {
  color: #cfeeff;
  text-decoration: underline;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
