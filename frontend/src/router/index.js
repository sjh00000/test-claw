import { createRouter, createWebHistory } from 'vue-router';
import HomeView from '../views/HomeView.vue';
import AdminView from '../views/AdminView.vue';
import DetailView from '../views/DetailView.vue';

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/review-admin', name: 'admin', component: AdminView },
    { path: '/review-detail/:id?', name: 'detail', component: DetailView, props: true }
  ]
});
