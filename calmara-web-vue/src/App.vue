<template>
  <router-view />
</template>

<script setup>
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

onMounted(() => {
  userStore.initializeAuth()
})
</script>

<style>
#app {
  width: 100%;
  height: 100vh;
}
</style>
