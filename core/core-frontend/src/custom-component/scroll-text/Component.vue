<script lang="ts" setup>
import { keycodes } from '@/utils/DeShortcutKey.js'
import eventBus from '@/utils/eventBus'
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { toRefs } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'

const canEdit = ref(false)
const ctrlKey = ref(17)
const isCtrlDown = ref(false)

const emit = defineEmits(['input'])
const text = ref(null)

const props = defineProps({
  propValue: {
    type: String,
    required: true,
    default: ''
  },
  element: {
    type: Object,
    default() {
      return {
        id: null,
        propValue: ''
      }
    }
  }
})

const { element } = toRefs(props)
const dvMainStore = dvMainStoreWithOut()
const { editMode, curComponent } = storeToRefs(dvMainStore)

const onComponentClick = () => {
  if (curComponent.value.id !== element.value.id) {
    canEdit.value = false
  }
}

const handleInput = e => {
  emit('input', element.value, e.target.innerHTML)
}

const handleKeydown = e => {
  // 阻止冒泡，防止触发复制、粘贴组件操作
  canEdit.value && e.stopPropagation()
  if (e.keyCode == ctrlKey.value) {
    isCtrlDown.value = true
  } else if (isCtrlDown.value && canEdit.value && keycodes.includes(e.keyCode)) {
    e.stopPropagation()
  } else if (e.keyCode == 46) {
    // deleteKey
    e.stopPropagation()
  }
}

const handleKeyup = e => {
  // 阻止冒泡，防止触发复制、粘贴组件操作
  canEdit.value && e.stopPropagation()
  if (e.keyCode == ctrlKey.value) {
    isCtrlDown.value = false
  }
}

const handleMousedown = e => {
  if (canEdit.value) {
    e.stopPropagation()
  }
}

const clearStyle = e => {
  e.preventDefault()
  const clp = e.clipboardData
  const text = clp.getData('text/plain') || ''
  if (text !== '') {
    document.execCommand('insertText', false, text)
  }
}

const handleBlur = e => {
  element.value.propValue = e.target.innerHTML || '&nbsp;'
  const html = e.target.innerHTML
  if (html !== '') {
    element.value.propValue = e.target.innerHTML
  } else {
    element.value.propValue = ''
    nextTick(function () {
      element.value.propValue = '&nbsp;'
    })
  }
  canEdit.value = false
}

const setEdit = () => {
  if (element.value['isLock']) return
  canEdit.value = true
  // 全选
  selectText(text.value)
}
const selectText = element => {
  const selection = window.getSelection()
  const range = document.createRange()
  range.selectNodeContents(element)
  selection.removeAllRanges()
  selection.addRange(range)
}
onBeforeUnmount(() => {
  eventBus.off('componentClick', onComponentClick)
})

const varStyle = computed(() => [{ '--scroll-speed': `${element.value.style.scrollSpeed}s` }])

const textStyle = computed(() => {
  return {
    verticalAlign: element.value['style'].verticalAlign
  }
})
</script>

<template>
  <div
    v-if="editMode == 'edit'"
    :style="varStyle"
    class="v-text"
    @keydown="handleKeydown"
    @keyup="handleKeyup"
  >
    <div
      ref="text"
      :contenteditable="canEdit"
      :class="{ 'can-edit': canEdit, 'marquee-txt': !canEdit }"
      tabindex="0"
      :style="textStyle"
      @dblclick="setEdit"
      @paste="clearStyle"
      @mousedown="handleMousedown"
      @blur="handleBlur"
      @input="handleInput"
    >
      {{ element['propValue'] }}
    </div>
  </div>
  <div v-else class="v-text preview" :style="varStyle">
    <div class="marquee-txt" :style="textStyle">{{ element['propValue'] }}</div>
  </div>
</template>

<style lang="less" scoped>
.v-text {
  width: 100%;
  height: 100%;
  display: table;
  div {
    display: table-cell;
    width: 100%;
    height: 100%;
    outline: none;
    word-break: break-all;
    padding: 4px;
    white-space: nowrap;
  }

  .can-edit {
    cursor: text;
    height: 100%;
  }
}

.preview {
  user-select: none;
}

.marquee {
  margin-left: 100px;
  width: 300px;
  white-space: nowrap;
  overflow: hidden;
  border: 1px solid #4c7cee;
}
.marquee-txt {
  display: inline-block;
  padding-left: 100%; /* 从右至左开始滚动 */
  animation: marqueeAnimation var(--scroll-speed) linear infinite;
}
@keyframes marqueeAnimation {
  0% {
    transform: translate(0, 0);
  }
  100% {
    transform: translate(-100%, 0);
  }
}
</style>
